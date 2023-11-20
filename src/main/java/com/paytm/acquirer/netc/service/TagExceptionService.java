package com.paytm.acquirer.netc.service;

import com.paytm.acquirer.netc.adapter.ExceptionAdapter;
import com.paytm.acquirer.netc.config.properties.RetryProperties;
import com.paytm.acquirer.netc.config.properties.TimeoutProperties;
import com.paytm.acquirer.netc.db.entities.AsyncTransaction;
import com.paytm.acquirer.netc.db.repositories.master.AsyncTransactionMasterRepository;
import com.paytm.acquirer.netc.dto.common.GetExceptionResponseXml;
import com.paytm.acquirer.netc.dto.getException.*;
import com.paytm.acquirer.netc.dto.kafka.ExceptionMessage;
import com.paytm.acquirer.netc.dto.kafka.ExceptionMetaInfo;
import com.paytm.acquirer.netc.dto.kafka.QueryExceptionMessage;
import com.paytm.acquirer.netc.dto.queryException.RedisTag;
import com.paytm.acquirer.netc.dto.queryException.ReqQueryExceptionDto;
import com.paytm.acquirer.netc.dto.queryException.ReqQueryExceptionListXml;
import com.paytm.acquirer.netc.dto.queryException.RespQueryExceptionListXml;
import com.paytm.acquirer.netc.enums.ExceptionCode;
import com.paytm.acquirer.netc.enums.NetcEndpoint;
import com.paytm.acquirer.netc.enums.Status;
import com.paytm.acquirer.netc.kafka.producer.KafkaProducer;
import com.paytm.acquirer.netc.service.common.*;
import com.paytm.acquirer.netc.util.*;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import com.paytm.transport.util.DynamicPropertyUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.HttpStatusCodeException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.paytm.acquirer.netc.adapter.KafkaAdapter.*;
import static com.paytm.acquirer.netc.enums.ListType.INIT_V2;
import static com.paytm.acquirer.netc.enums.NetcEndpoint.GET_EXCEPTION_LIST;
import static com.paytm.acquirer.netc.enums.NetcEndpoint.QUERY_EXCEPTION_LIST;
import static com.paytm.acquirer.netc.enums.Status.RESPONSE_RECEIVED;
import static com.paytm.acquirer.netc.util.Constants.DIFF_KEY_PREFIX;
import static com.paytm.acquirer.netc.util.Constants.INIT_EXCEPTION_MAP;
import static com.paytm.acquirer.netc.util.Constants.INIT_EXCEPTION_SET;
import static com.paytm.acquirer.netc.util.Utils.getKeyFromPrefixAndMsgId;

@Service
@RequiredArgsConstructor
public class TagExceptionService {
  private static final Logger log = LoggerFactory.getLogger(TagExceptionService.class);

  private final NetcClient netcClient;
  private final SignatureService signatureService;
  private final MetadataService metadataService;
  private final KafkaProducer producer;
  private final AsyncTxnService asyncTxnService;
  private final RedisService redisService;
  private final AsyncTransactionMasterRepository asyncTransactionMasterRepository;
  private final RetryProperties retryProperties;
  private final TimeoutProperties timeoutProperties;
  private final ReminderService reminderService;
  private final S3ServiceWrapper s3ServiceWrapper;
  private final ValidateService validateService;
  private final CustomMetricService customMetricService;

  @Value("${tas.npci.time-diff}")
  private Long tasNpciTimeDiff;

  private static Logger failureLogger = LoggerFactory.getLogger(TagExceptionService.class);

  public void queryExceptionList(int retryCount, ReqQueryExceptionDto reqQueryExceptionDto) {
    log.info("queryException request with retry count {}", retryCount);
    if (retryCount > retryProperties.getReqQueryExceptionMaxRetry()) {
      log.error("retry count more then allowed. Dropping request. count : {}", retryCount);
      return;
    }

    if(validateService.isInvalidBodyOfDiffRequest(reqQueryExceptionDto, retryCount)) {
      return;
    }

    String uid = reqQueryExceptionDto.getUid();
    String lastUpdatedTime = reqQueryExceptionDto.getLastUpdatedTime();

    // create common timeStamp
    String requestTimeStamp = Utils.getFormattedDate(tasNpciTimeDiff);
    log.info("DIFF Duration:{}-{}", lastUpdatedTime, requestTimeStamp);

    Map<String,String> additionalParams = new HashMap<>();
    additionalParams.put(Constants.UID, uid);
    additionalParams.put(Constants.LAST_UPDATED_TIME, lastUpdatedTime);
    if(reqQueryExceptionDto.isTestRequestViaAPI()) {
      additionalParams.put(Constants.TEST_REQUEST_VIA_API, Boolean.toString(reqQueryExceptionDto.isTestRequestViaAPI()));
    }

    ReqQueryExceptionListXml request =
      ExceptionAdapter.queryExceptionListDto(requestTimeStamp, lastUpdatedTime, metadataService);
    request.setHeader(
      metadataService.createXmlHeader(request.getTransaction().getId(), requestTimeStamp, null));
    request
      .getTransaction()
      .setOriginalTransactionId(metadataService.getOrgTxnId(request.getTransaction().getId()));

    String signedXml = signatureService.signXmlDocument(XmlUtil.serializeXmlDocument(request));

    int statusCode = -1;
    try {
      log.info("<--- hitting queryExceptionList with data : {}", signedXml);
      asyncTxnService.createEntry(asyncTxnService.buildAsyncTxnRecord(request.getHeader().getMessageId(),
        request.getTransaction(), Status.INITIATING_REQUEST, QUERY_EXCEPTION_LIST, "", additionalParams));
      ResponseEntity<Void> responseEntity = netcClient.requestQueryExceptionList(signedXml);
      if (responseEntity != null) {
        statusCode = responseEntity.getStatusCodeValue();
      }
      reminderService.addReminder(
        queryExceptionKafkaMsg(
          request.getHeader().getMessageId(),
          String.valueOf(1),
          String.valueOf(1),
          String.valueOf(retryCount + 1),
          uid, reqQueryExceptionDto.isTestRequestViaAPI()),
        null,
        timeoutProperties.getFirstRespQueryExceptionTimeout().longValue()
      );
      // record progress
      asyncTxnService.updateEntry(request.getHeader().getMessageId(), RESPONSE_RECEIVED, Integer.toString(statusCode),
        NetcEndpoint.QUERY_EXCEPTION_LIST);
    } catch (Exception ex) {
      log.info("error while calling reqQueryExceptionList :{}", ex.getLocalizedMessage());

      if (ex instanceof HttpStatusCodeException) {
        HttpStatusCodeException exception = (HttpStatusCodeException) ex;
        statusCode = exception.getStatusCode().value();
      }

      // record failure status

      asyncTxnService.updateEntry(request.getHeader().getMessageId(), Status.REQUEST_FAILED,
        Integer.toString(statusCode), NetcEndpoint.QUERY_EXCEPTION_LIST);
      if(reqQueryExceptionDto.isTestRequestViaAPI()) {
        return;
      }

      if (retryCount < retryProperties.getReqQueryExceptionMaxRetry()) {
        reminderService.addReminder(
          queryExceptionNewRequestKafkaMsg(request.getHeader().getMessageId(),
            retryCount + 1,uid,lastUpdatedTime),
          null,
          timeoutProperties.getReqQueryExceptionLocalRetryTimeout().longValue()
        );
      } else {
        // TPT-4428: After retrying a limited number of times just generate Empty DIFF File
        generateEmptyQueryExceptionData(request.getHeader().getMessageId(), uid, false, LocalDateTime.now());
        failureLogger.error("max local retry reached for get exception list . Please check. data:{}", request);
      }

      throw ex;
    } finally {
      log.info("status code for queryExceptionList is :{}", statusCode);
    }
  }

  /*
   * This method just generates empty DIFF data for exception-handler to consume
   * @see "TPT-4428"
   */
  public void generateEmptyQueryExceptionData(String messageId, String uid, boolean isDiffViaSftp,
    LocalDateTime fetchStartTime) {
    log.info("Generating Empty DIFF data for msgId {}", messageId);
    processFinalDiffData(messageId, Utils.getFormattedDate(), uid, true, isDiffViaSftp,
      null, null, fetchStartTime, 0);
  }

//  @Async("exception-list-executor")
  public void acceptGetExceptionListData(RespGetExceptionListXml responseData) {
    final String messageId = responseData.getHeader().getMessageId();
    RespGetExceptionListXml.RespGetExceptionTransactionXml transaction =
      responseData.getTransaction();

    // check if response has any errors
    if (!Constants.RESULT_SUCCESS.equals(transaction.getResponse().getResult())
      || transaction.getResponse().getExceptionList().stream()
      .anyMatch(exLst -> !Constants.RESULT_SUCCESS.equals(exLst.getResult()))) {
      log.error("Got errors in the response: {}", XmlUtil.serializeXmlDocument(responseData));
      // mark as failed
      asyncTxnService.updateEntry(messageId, Status.REQUEST_FAILED, GET_EXCEPTION_LIST);
      // remove existing data from redis
      redisService.purgeInitData(messageId);
      return;
    }

    // if not, then save data in redis
  
    String initTagMapKey = INIT_EXCEPTION_MAP + messageId;
    String initTagSetKey =
      INIT_EXCEPTION_SET
        + messageId
        + "_"
        + getSetNumberBasedOnMessageNumber(
        responseData.getTransaction().getResponse().getMessageNumber());

    Map<String, Integer> mapOfTagIdAndExceptionCodeSum = new HashMap<>();
    
    List<GetExceptionResponseXml.Exceptions> exceptionsList = responseData.getTransaction().getResponse().getExceptionList();
    processExceptionResponseList(mapOfTagIdAndExceptionCodeSum, exceptionsList);

    List<String> tagIdList = new ArrayList<>(mapOfTagIdAndExceptionCodeSum.keySet());
    List<Integer> existingExceptionCodeSumList = redisService.getInitTagsExceptionCodesFromMap(initTagMapKey, tagIdList);

    Set<String> uniqueTagSet = new HashSet<>();
    IntStream.range(0, tagIdList.size())
      .forEach(index -> {
        Integer exceptionValue = existingExceptionCodeSumList.get(index);
        if (Objects.isNull(exceptionValue)) {
          uniqueTagSet.add(tagIdList.get(index));
          return;
        }
        Integer exceptionCodeSum = mapOfTagIdAndExceptionCodeSum.get(tagIdList.get(index)) | exceptionValue;
        mapOfTagIdAndExceptionCodeSum.put(tagIdList.get(index), exceptionCodeSum);
      });
    
    redisService.saveGetExceptionDataMap(initTagMapKey, mapOfTagIdAndExceptionCodeSum);
    redisService.saveGetExceptionData(initTagSetKey, uniqueTagSet.stream().collect(Collectors.toList()));

    // if this message is the last message then update progress in DB & send kafka message
    if (transaction.getResponse().getMessageNumber()
      == transaction.getResponse().getTotalMessage()) {

      String lastUpdatedTime =
        transaction.getResponse().getExceptionList().get(0).getLastUpdatedTime();
      ExceptionMetaInfo additionalParam =
        asyncTxnService.getAdditionalParamByMsgId(
          responseData.getHeader().getMessageId(),
          ExceptionMetaInfo.class);
      int totalTagSetsInRedis = getSetNumberBasedOnMessageNumber(transaction.getResponse().getMessageNumber());
      String initExceptionSetPrefix = INIT_EXCEPTION_SET + messageId + "_";
      ExceptionMessage message =
        new ExceptionMessage(
          INIT_V2, responseData.getHeader().getMessageId(), lastUpdatedTime, initTagMapKey,initExceptionSetPrefix ,
          totalTagSetsInRedis, additionalParam);
      if (redisService.isInitCompletionFlagExists()) {
        log.info("Init file generation is already processed for the day ");
        asyncTxnService.updateEntry(messageId, Status.RESPONSE_RECEIVED_SFTP, GET_EXCEPTION_LIST);
        return;
      }
      redisService.setInitCompletionFlag(true);
      producer.sendExceptionListKeys(message);

    } else {
      reminderService.addReminder(
        getExceptionKafkaMsg(
          responseData.getHeader().getMessageId(),
          String.valueOf(transaction.getResponse().getMessageNumber() + 1),
          String.valueOf(transaction.getResponse().getTotalMessage()),
          "0"),
        null,
        timeoutProperties.getConsecutiveRespGetExceptionTimeout().longValue()
      );
    }
  }

  private void processExceptionResponseList(Map<String, Integer> mapOfTagIdAndExceptionCodeSum, List<GetExceptionResponseXml.Exceptions> exceptionsList) {
    if (Objects.nonNull(exceptionsList)) {
      for (GetExceptionResponseXml.Exceptions exception : exceptionsList) {
        if (Objects.nonNull(exception) && Objects.nonNull(exception.getTagList())) {
          for (GetExceptionResponseXml.Tag tag : exception.getTagList()) {
            if (Objects.nonNull(tag) && Objects.nonNull(tag.getTagId())) {
              processTagFromList(mapOfTagIdAndExceptionCodeSum, exception, tag);
            }
          }
        } else {
          log.info("Init message received {}", exceptionsList);
        }
      }
    }
  }

  private void processTagFromList(
    Map<String, Integer> mapOfTagIdAndExceptionCodeSum,
    GetExceptionResponseXml.Exceptions exception,
    GetExceptionResponseXml.Tag tag) {
    if (Objects.nonNull(mapOfTagIdAndExceptionCodeSum.get(tag.getTagId()))) {
      Integer exceptionCodeSum = mapOfTagIdAndExceptionCodeSum.get(tag.getTagId());
      mapOfTagIdAndExceptionCodeSum.put(
        tag.getTagId(),
        (exceptionCodeSum | ExceptionCode.getBinaryValue(exception.getExceptionCode())));
    } else {
      mapOfTagIdAndExceptionCodeSum.put(
        tag.getTagId(), ExceptionCode.getBinaryValue(exception.getExceptionCode()));
    }
  }

  public static int getSetNumberBasedOnMessageNumber(int messageNumber){
        Integer setDivisionFactor =
            DynamicPropertyUtil.getIntPropertyValue(
                Constants.DynamicConfig.Key.INIT_SET_DIVISION_FACTOR,
                Constants.DynamicConfig.DefaultValue.INIT_SET_DIVISION_FACTOR);

        return ((messageNumber -1)/setDivisionFactor) +1;
  }

  @Async("exception-list-executor")
  public void acceptQueryExceptionListData(RespQueryExceptionListXml responseData,
    String uid, boolean testRequestViaAPI, AsyncTransaction requestData) {
    log.info("handling response in separate thread.");

    final String messageId = responseData.getHeader().getMessageId();
    RespQueryExceptionListXml.RespQueryExceptionTransactionXml transaction =
      responseData.getTransaction();

    // check if response has any errors
    if (!Constants.RESULT_SUCCESS.equals(transaction.getResponse().getResult())
      || transaction.getResponse().getExceptionList().stream()
      .anyMatch(exLst -> !Constants.RESULT_SUCCESS.equals(exLst.getResult()))) {
      log.error("Got errors in the response: {}", XmlUtil.serializeXmlDocument(responseData));
      // mark as failed
      asyncTxnService.updateEntry(messageId, Status.REQUEST_FAILED, QUERY_EXCEPTION_LIST);
      // remove existing data from redis
      redisService.purgeDiffData(messageId);
      return;
    }

    if(testRequestViaAPI) {
      changeChannelToFetchDiffViaAPI();
      return;
    }

    // if not, then save data in redis
    List<RedisTag> exceptionData =
      responseData.getTransaction().getResponse().getExceptionList().stream()
        .filter(ex -> ex.getTagList() != null)
        .flatMap(
          exception ->
            exception.getTagList().stream()
              .map(tag -> new RedisTag(exception.getExceptionCode(), tag)))
        .collect(Collectors.toList());

    // persist each item
    String redisKey = getKeyFromPrefixAndMsgId(DIFF_KEY_PREFIX, messageId);
    redisService.saveQueryExceptionData(redisKey, exceptionData);

    // if this message is the last message then update progress in DB & send kafka message

    if (responseData.getTransaction().getResponse().getMessageNumber() ==
      responseData.getTransaction().getResponse().getTotalMessage()) {
      processFinalDiffData(messageId, responseData.getHeader().getTimeStamp(), uid, false,
        false, null, null,
        requestData.getCreatedAt().toLocalDateTime(), 0);
    }
    else {
      reminderService.addReminder(
        queryExceptionKafkaMsg(
          responseData.getHeader().getMessageId(),
          String.valueOf(transaction.getResponse().getMessageNumber() + 1),
          String.valueOf(transaction.getResponse().getTotalMessage()),
          "0",
          uid, false),
        null,
        timeoutProperties.getConsecutiveRespQueryExceptionTimeout().longValue());
    }
  }

  private void changeChannelToFetchDiffViaAPI() {
    int minDiffSuccessViaTestApi = DynamicPropertyUtil.getIntPropertyValue(
      Constants.DynamicConfig.Key.MIN_DIFF_SUCCESS_VIA_TEST_API,
      Constants.DynamicConfig.DefaultValue.MIN_DIFF_SUCCESS_VIA_TEST_API);
    if(minDiffSuccessViaTestApi > 1) {
      List<AsyncTransaction> requestAsyncTransactionList = asyncTransactionMasterRepository.
        findByApiOrderByIdDesc(QUERY_EXCEPTION_LIST, PageRequest.of(0, minDiffSuccessViaTestApi));
      boolean hasAllDiffSuccess = requestAsyncTransactionList.stream()
        .allMatch(triggerStatus -> {
          Map<String, String> meta = JsonUtil.parseJsonToMap(triggerStatus.getMetaData(), String.class, String.class);
          if(CollectionUtils.isEmpty(meta)) {
            return false;
          }

          boolean testRequestViaAPI = Boolean.parseBoolean(meta.getOrDefault(
            Constants.TEST_REQUEST_VIA_API, "false"));
          return testRequestViaAPI && triggerStatus.getStatus().equals(RESPONSE_RECEIVED);
        });

      if(!hasAllDiffSuccess) {
        return;
      }
    }
    redisService.setFetchDiffViaAPI();
  }

  public void processFinalDiffData(String messageId, String responseTimestamp, String uid,
    Boolean isEmptyDiff, boolean isDiffViaSftp, String lastTagUpdatedTime, String lastSftpFileName,
    LocalDateTime fetchStartTime, Integer totalFiles) {

    String redisKey = getKeyFromPrefixAndMsgId(DIFF_KEY_PREFIX, messageId);
    Long count = redisService.getQueryExceptionTagsCount(redisKey);
    try {
      s3ServiceWrapper.createFile(messageId, redisService.getQueryExceptionTags(redisKey, count));
    }
    catch (Exception e) {
      log.error("error saving file in s3 {}", e, messageId);
    }

    QueryExceptionMessage exceptionMessage = new QueryExceptionMessage();
    exceptionMessage.setFetchTime(responseTimestamp);
    exceptionMessage.setMsgId(messageId);
    exceptionMessage.setKey(messageId);
    exceptionMessage.setTotalTags(count);
    exceptionMessage.setIsEmptyDiff(isEmptyDiff);
    exceptionMessage.setUid(uid);
    exceptionMessage.setNetcEngineClockOffset(Utils.getNetcEngineClockOffset());
    exceptionMessage.setIsDiffViaSftp(isDiffViaSftp);
    exceptionMessage.setLastTagUpdatedTime(lastTagUpdatedTime);
    exceptionMessage.setLastSftpFileName(lastSftpFileName);

    producer.publishQueryExceptionData(exceptionMessage);
    log.info("published {} data for query exception with msgId {}", exceptionMessage, messageId);

    customMetricService.recordCompleteDiffLog(messageId, isDiffViaSftp, count, isEmptyDiff, fetchStartTime, totalFiles);
  }

}
