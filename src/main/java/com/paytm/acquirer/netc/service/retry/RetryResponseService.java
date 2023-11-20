package com.paytm.acquirer.netc.service.retry;

import com.paytm.acquirer.netc.adapter.ExceptionAdapter;
import com.paytm.acquirer.netc.adapter.KafkaAdapter;
import com.paytm.acquirer.netc.adapter.PayAdapter;
import com.paytm.acquirer.netc.config.properties.RetryProperties;
import com.paytm.acquirer.netc.config.properties.TimeoutProperties;
import com.paytm.acquirer.netc.db.entities.AsyncTransaction;
import com.paytm.acquirer.netc.db.entities.ErrorCodeMapping;
import com.paytm.acquirer.netc.db.repositories.master.AsyncTransactionMasterRepository;
import com.paytm.acquirer.netc.db.repositories.master.ErrorCodeMappingMasterRepository;
import com.paytm.acquirer.netc.dto.getException.RespGetExceptionListXml;
import com.paytm.acquirer.netc.dto.pay.RespPay;
import com.paytm.acquirer.netc.dto.pay.RespPayXml;
import com.paytm.acquirer.netc.dto.queryException.RespQueryExceptionListXml;
import com.paytm.acquirer.netc.enums.ErrorMessage;
import com.paytm.acquirer.netc.enums.HandlerType;
import com.paytm.acquirer.netc.enums.NetcEndpoint;
import com.paytm.acquirer.netc.enums.Status;
import com.paytm.acquirer.netc.exception.NetcEngineException;
import com.paytm.acquirer.netc.kafka.producer.KafkaProducer;
import com.paytm.acquirer.netc.service.*;
import com.paytm.acquirer.netc.service.common.RedisService;
import com.paytm.acquirer.netc.service.common.SignatureService;
import com.paytm.acquirer.netc.util.Constants;
import com.paytm.acquirer.netc.util.JsonUtil;
import com.paytm.acquirer.netc.util.XmlUtil;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import com.paytm.transport.metrics.Monitor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.paytm.acquirer.netc.enums.NetcEndpoint.GET_EXCEPTION_LIST;
import static com.paytm.acquirer.netc.enums.NetcEndpoint.RESP_PAY;
import static com.paytm.acquirer.netc.enums.Status.RESPONSE_RECEIVED;
import static com.paytm.acquirer.netc.service.common.MetadataService.getRetryCountFromMsgId;
import static com.paytm.acquirer.netc.util.Constants.*;
import static com.paytm.transport.metrics.Monitor.ServiceGroup.API_IN;

@Service
@RequiredArgsConstructor
public class RetryResponseService {
  private static final Logger log = LoggerFactory.getLogger(RetryResponseService.class);

  private final AsyncTransactionMasterRepository asyncTransactionMasterRepository;
  private final AsyncTxnService asyncTxnService;
  private final TagExceptionService tagExceptionService;
  private final RedisService redisService;
  private final RespPayService respPayService;
  private final ErrorCodeMappingMasterRepository errorCodeMappingMasterRepository;
  private final KafkaProducer kafkaProducer;
  private final RetryProperties retryProperties;
  private final SignatureService signatureService;
  private final CustomMetricService customMetricService;
  private final ReminderService reminderService;
  private final TimeoutProperties timeoutProperties;
  
  @Value("${netc.exception-list.ack-delay-ms}")
  private Integer ackDelayInMs;

  private static final Logger failureLogger = LoggerFactory.getLogger(RetryResponseService.class);

  public void handleGetExceptionListData(RespGetExceptionListXml responseData) {
    if (redisService.isMsgIdTimedOut(responseData.getHeader().getMessageId())) {
      // Since this request got timed-out, there is another request being retried by different flow
      // so we're going to ignore responses of this request
      log.info("Got response of timed out INIT request with msgId {}. Ignoring it.",
          responseData.getHeader().getMessageId());
      throw new NetcEngineException(ErrorMessage.GET_EXCEPTION_CALLBACK_TIMED_OUT,
        responseData.getHeader().getMessageId());
    }
    kafkaProducer.sendInitCompressedData(responseData);
  }
  
  public void handleInitCallBackData(RespGetExceptionListXml responseData) {
    
    // Validate request exists
    Optional<AsyncTransaction> request = asyncTxnService.findByMsgIdAndApi(
      responseData.getHeader().getMessageId(), NetcEndpoint.GET_EXCEPTION_LIST);
    
    if(!request.isPresent()) {
      //discard data in redis
      redisService.purgeInitData(responseData.getHeader().getMessageId());
      redisService.timeoutMsgId(responseData.getHeader().getMessageId());
      return;
    }
    
    AsyncTransaction requestTransaction = request.get();
    
    if (redisService.isInitCompletionFlagExists() ||
      requestTransaction.getStatus().equals(Status.RESPONSE_RECEIVED_SFTP)) {
      asyncTxnService.updateEntry(requestTransaction.getMsgId(), Status.RESPONSE_RECEIVED_SFTP, GET_EXCEPTION_LIST);
      createSFTPProcessEntry(responseData);
      log.info("Init file data is already processed for the day {} ", LocalDate.now());
      //discard data in redis
      redisService.purgeInitData(responseData.getHeader().getMessageId());
      redisService.timeoutMsgId(responseData.getHeader().getMessageId());
      return;
    }
    
    Optional<AsyncTransaction> optional = asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(
      responseData.getHeader().getMessageId(),
      NetcEndpoint.RESP_GET_EXCEPTION_LIST,
      responseData.getTransaction().getResponse().getMessageNumber());
    
    //check entry in db for this response
    if (optional.isPresent()) {
      log.info("RespGetException already present in database with status {}. Need to discard this", optional.get().getStatus());
      
      // TODO : @Drohilla discard data in redis why this ? if a request is received twice, we discard entire diff
      redisService.purgeInitData(responseData.getHeader().getMessageId());
      redisService.timeoutMsgId(responseData.getHeader().getMessageId());
    } else {
      log.info("RespGetException not found in database. Adding entry and allocating work to separate thread");
      
      AsyncTransaction transaction = ExceptionAdapter.getAsyncTxnFromXml(responseData);
      asyncTransactionMasterRepository.save(transaction);
      
      tagExceptionService.acceptGetExceptionListData(responseData);
    }
  
  }
  
  @Monitor(name = "DIFF_callback", metricGroup = API_IN)
  public ResponseEntity<String> prepareCallbackResponseData(String data) {
    long t2 = System.currentTimeMillis();
    if (!signatureService.isXmlValid(data)) {
      log.info("Signature is not valid as signature validation fails");
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    long t3 = System.currentTimeMillis();
    RespQueryExceptionListXml request = XmlUtil.deserializeXmlDocument(data, RespQueryExceptionListXml.class);
    long t4 = System.currentTimeMillis();
    Assert.hasLength(request.getHeader().getMessageId(), "Message Id cannot be empty");
    log.info("Got DIFF response for msgId {}, msgNum {}", request.getHeader().getMessageId(),
      request.getTransaction().getResponse().getMessageNumber());
    handleQueryExceptionListData(request);
    long t5 = System.currentTimeMillis();
    log.info(" Times StreamCopy:{} SigVerify:{} Deserialize:{} RunLogic:{} Total:{} ms"
      , t3 - t2, t4 - t3, t5 - t4,t5 - t2);
    return new ResponseEntity<>(HttpStatus.ACCEPTED);
  }
  public void handleQueryExceptionListData(RespQueryExceptionListXml responseData) {

    if (redisService.isMsgIdTimedOut(responseData.getHeader().getMessageId())) {
      // Since this request got timed-out, there is another request being retried by different flow
      // so we're going to ignore responses of this request
      log.info("Got response of timed out DIFF request with msgId {}. Ignoring it.",
          responseData.getHeader().getMessageId());
      return;
    }

    // Validate request exists
    Optional<AsyncTransaction> request = asyncTransactionMasterRepository.findByMsgIdAndApi(
      responseData.getHeader().getMessageId(), NetcEndpoint.QUERY_EXCEPTION_LIST);

    if(!request.isPresent()) {
      customMetricService.recordMetricForUnknownMessageId("DIFF");
      throw new NetcEngineException("Got response callback for unknown msgId "
        + responseData.getHeader().getMessageId());
    }

    AsyncTransaction requestData = request.get();
    Optional<AsyncTransaction> optional = asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(
        responseData.getHeader().getMessageId(),
        NetcEndpoint.RESP_QUERY_EXCEPTION_LIST,
        responseData.getTransaction().getResponse().getMessageNumber());

    customMetricService.recordElapsedTimeForDiffResp(requestData, responseData);

    //check entry in db for this response
    if (optional.isPresent()) {
      log.info("RespQueryException already present in database with status {}. Need to discard old data", optional.get().getStatus());

      //discard data in redis
      redisService.purgeDiffData(responseData.getHeader().getMessageId());
      redisService.timeoutMsgId(responseData.getHeader().getMessageId());
    }
    else {
      log.info("RespGetException not found in database. Adding entry and allocating work to separate thread");

      AsyncTransaction transaction = ExceptionAdapter.getAsyncTxnFromXml(responseData);
      asyncTransactionMasterRepository.save(transaction);

      String metaData = request.get().getMetaData();
      String uid = "";
      boolean testRequestViaAPI = false;
      if(Objects.nonNull(metaData)) {
        try {
          Map<String, String> meta = JsonUtil.parseJsonToMap(metaData, String.class, String.class);
          if(!CollectionUtils.isEmpty(meta)) {
            uid = meta.getOrDefault(Constants.UID, "");
            testRequestViaAPI = Boolean.parseBoolean(meta.getOrDefault(Constants.TEST_REQUEST_VIA_API, "false"));
          }
        } catch (Exception e) {
          log.error(
            "Error while serializing additional param for messageId {} : {} ",
            responseData.getHeader().getMessageId(), e.getMessage());
        }
      }
      tagExceptionService.acceptQueryExceptionListData(responseData, uid, testRequestViaAPI, requestData);
    }

  }

  public void handleRespPay(RespPayXml respPayXml) {
    // Check if corresponding request exists
    AsyncTransaction request = asyncTxnService.checkAndGetReqPay(respPayXml.getHeader().getMessageId());
    Optional<AsyncTransaction> response = asyncTxnService.checkIfResponseReceived(NetcEndpoint.RESP_PAY,
        respPayXml.getHeader().getMessageId(), 1);

    customMetricService.recordElapsedTimeExecutionMetric(Monitor.ServiceGroup.TIME_TAKEN,
      request.getCreatedAt().getTime(), NPCI_RESPONSE_TIME);
    if (response.isPresent()) {
      log.info("RespPay already present in db with status {}. Skipping this.", response.get().getStatus());
      return;
    }

    //delete retry trigger from reminder service
    reminderService.deleteReminder(respPayXml.getHeader().getMessageId());
    String responseCode = respPayXml.getResponse().getResponseCode();
    List<String> responseCodeList = Arrays.asList(responseCode.split(","));
    // mark it as received
    asyncTxnService.createEntryForRespPay(respPayXml.getHeader().getMessageId(),
        respPayXml.getRiskScoreTxn().getId(), request.getRefId(), RESPONSE_RECEIVED,
        responseCode);

    RespPay respPay = PayAdapter.respPayXmlToRespPayKafka(respPayXml, request.getRefId());
    // TPT-5228: Consider 201 error code as success
    if (responseCodeList.contains("201")) {
      log.info("Got 201 error code for MsgId {}, TxnId {}. Sending it as Accepted Transaction.",
          respPayXml.getHeader().getMessageId(), respPayXml.getRiskScoreTxn().getId());
      respPay.setResult("ACCEPTED");
      respPay.setErrorCodes("000");
      respPay.setNetcResponseTime(asyncTxnService.findTxnSuccessfulTimeByRefId(respPay.getRefId()));
    }

    respPay.setRequestTime(asyncTxnService.getRequestTime(respPay.getRefId(), respPay.getNetcResponseTime()));

    String metaData  = request.getMetaData();
    String plazaId = "";
    if(!StringUtils.isEmpty(metaData)){
      try {
        Map<String,String> meta = JsonUtil.parseJsonToMap(metaData,String.class,String.class);
        if(!CollectionUtils.isEmpty(meta))
          plazaId = meta.getOrDefault(PLAZA_ID,"");
      } catch (Exception e) {
        log.error(
          "Error while serializing additional param for messageId {} : {} ",
          respPayXml.getHeader().getMessageId(), e.getMessage());
      }
    }
    respPay.setSettledPlazaId(plazaId);
    respPayService.handleData(respPay);

    handleReqPayRetryScenarios(respPayXml, request);
  }

  private void handleReqPayRetryScenarios(RespPayXml respPayXml, AsyncTransaction request) {
    String responseCode = respPayXml.getResponse().getResponseCode();
    List<String> responseCodeList = Arrays.asList(responseCode.split(","));

    // success
    if (SUCCESS_STATUS_LIST.contains(respPayXml.getResponse().getResult())) {
      if (!SUCCESS_RESPONSE_CODE_LIST.contains(responseCode)) {
        log.error("resp pay with result as : {} and error code as : {}", respPayXml.getResponse().getResult(), responseCode);
      }
    }
    else if (!responseCodeList.contains("201")) {
      // * get list of exception mappings
      List<ErrorCodeMapping> errorCodeMappings = errorCodeMappingMasterRepository.findByHandlerNotNull();
      Map<String, ErrorCodeMapping> retryableErrorCodes = errorCodeMappings.stream()
        .collect(Collectors.toMap(ErrorCodeMapping::getErrorCode, Function.identity()));

      if (retryableErrorCodes.keySet().containsAll(responseCodeList)) {
        List<ErrorCodeMapping> mappings = responseCodeList.stream()
          .map(retryableErrorCodes::get).collect(Collectors.toList());
        if (mappings.stream().map(ErrorCodeMapping::getHandler).collect(Collectors.toSet()).size() == 1) {
          handleReqPayRetryScenarios(respPayXml, mappings.get(0).getHandler(), request.getRefId());
        }
        else {
          failureLogger.error("Got some retryable error code: {} for RespPay msgId: {}", responseCode,
            respPayXml.getHeader().getMessageId());
        }
      }
      else {
        failureLogger.error("Got non-retryable error code: {} for RespPay msgId: {}", responseCode,
          respPayXml.getHeader().getMessageId());
      }
    }
  }

  private void handleReqPayRetryScenarios(RespPayXml respPayXml, HandlerType type, String refId) {
    log.info("handling response with handler {}", type.name());
    switch (type) {
      case AUTO_RETRY:
        asyncTxnService.updateEntry(respPayXml.getHeader().getMessageId(), Status.AUTO_RETRY_CODE, RESP_PAY);
        if (getRetryCountFromMsgId(respPayXml.getHeader().getMessageId()) < retryProperties.getReqPayMaxRetry()) {
          reminderService.addReminder(KafkaAdapter.createKafkaMsgForRetrialCodeReqPay(respPayXml, refId), null, timeoutProperties.getNpciAutoRetryTimeout().longValue());
        } else {
          failureLogger.info("AUTO_RETRY : max retry reached for this txn. Not sending any more reties. codes:{}, txnId:{}, msgId:{}",
              respPayXml.getResponse().getResponseCode(),
              respPayXml.getRiskScoreTxn().getId(),
              respPayXml.getHeader().getMessageId()
          );
        }
        break;

      case MANUAL_RETRY:
        asyncTxnService.updateEntry(respPayXml.getHeader().getMessageId(), Status.MANUAL_RETRY_CODE, RESP_PAY);
        failureLogger.info("MANUAL_RETRY: codes {}, txnId: {}, msgId: {}",
            respPayXml.getResponse().getResponseCode(),
            respPayXml.getRiskScoreTxn().getId(),
            respPayXml.getHeader().getMessageId()
        );
        break;

      case AUTO_BLACK_LIST:
        log.info("Got error code which should be BlackListed by Acquirer Engine: msgId: {}, codes {}",
            respPayXml.getHeader().getMessageId(),
            respPayXml.getResponse().getResponseCode());
        break;
    }
  }

  private void createSFTPProcessEntry(RespGetExceptionListXml data) {
    asyncTxnService.createEntry(
      AsyncTransaction.builder()
        .msgId(data.getHeader().getMessageId())
        .txnId(data.getTransaction().getId())
        .status(Status.RESPONSE_RECEIVED_SFTP)
        .api(NetcEndpoint.RESP_GET_EXCEPTION_LIST)
        .statusCode(data.getTransaction().getResponse().getResponseCode())
        .msgNum(data.getTransaction().getResponse().getMessageNumber())
        .totalMsg(data.getTransaction().getResponse().getTotalMessage())
        .build());
  }
}
