package com.paytm.acquirer.netc.service.retry;

import com.paytm.acquirer.netc.config.properties.RetryProperties;
import com.paytm.acquirer.netc.config.properties.TimeoutProperties;
import com.paytm.acquirer.netc.db.entities.AsyncTransaction;
import com.paytm.acquirer.netc.db.entities.ErrorCodeMapping;
import com.paytm.acquirer.netc.db.repositories.master.ErrorCodeMappingMasterRepository;
import com.paytm.acquirer.netc.dto.kafka.ExceptionMetaInfo;
import com.paytm.acquirer.netc.dto.pay.ReqPay;
import com.paytm.acquirer.netc.dto.pay.RespPay;
import com.paytm.acquirer.netc.dto.retry.ReqPayRetry;
import com.paytm.acquirer.netc.dto.retry.RetryDto;
import com.paytm.acquirer.netc.dto.transactionstatus.RespCheckTransactionXml;
import com.paytm.acquirer.netc.dto.transactionstatus.TransactionReq;
import com.paytm.acquirer.netc.enums.NetcEndpoint;
import com.paytm.acquirer.netc.enums.Status;
import com.paytm.acquirer.netc.kafka.producer.KafkaProducer;
import com.paytm.acquirer.netc.service.*;
import com.paytm.acquirer.netc.service.common.RedisService;
import com.paytm.acquirer.netc.util.Constants;
import com.paytm.acquirer.netc.util.JsonUtil;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import com.paytm.transport.metrics.Monitor;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import com.paytm.acquirer.netc.enums.HandlerType;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.Optional;

import static com.paytm.acquirer.netc.enums.NetcEndpoint.*;
import static com.paytm.acquirer.netc.service.common.MetadataService.getMetaData;
import static com.paytm.acquirer.netc.service.common.MetadataService.getRetryCountFromMsgId;
import static com.paytm.acquirer.netc.service.common.MetadataService.getTxnIdFromMsgId;
import static com.paytm.acquirer.netc.util.Constants.*;

@Service
@RequiredArgsConstructor
public class CronRetryService {
  private static final Logger log = LoggerFactory.getLogger(CronRetryService.class);

  private final AsyncTxnService asyncTxnService;
  private final RetryProperties retryLimit;
  private final KafkaProducer kafkaProducer;
  private final TagExceptionService tagExceptionService;
  private final RedisService redisService;
  private final RetryProperties retryProperties;
  private final ErrorCodeMappingMasterRepository errorCodeMappingRepository;
  private final RespPayService respPayService;
  private final CheckTransactionService checkTransactionService;
  private final CustomMetricService customMetricService;
  private final ReminderService reminderService;
  private final TimeoutProperties timeoutProperties;
  private final InitService initService;

  // TODO: will handle later
  private static Logger failureLogger = LoggerFactory.getLogger(CronRetryService.class);

  /**
   * FOR: RESP_PAY
   * <p>
   * Called by reminder service to notify timeout
   * and retry if necessary.
   * <p>
   * Ignore if we already got the response or else
   * check transaction status if we need to retry or not.
   * If transaction succeeded on NETC switch then trigger respPay flow
   * or else mark as timeout and retry unless limit is crossed.
   *
   * @param retryDto meta data to identify request and retry operation
   */
  public void respPayRetry(RetryDto retryDto) {
    if (!retryDto.getParams().containsKey(Constants.ReqPay.CHECK_TXN_RETRY_COUNT)) {
      log.info("Resp pay retry received for msg id {}",retryDto.getMsgId());
      Optional<AsyncTransaction> transaction = asyncTxnService.checkIfResponseReceived(NetcEndpoint.RESP_PAY,
        retryDto.getMsgId(), 1);
      if (transaction.isPresent()) {
        customMetricService.recordCountMetric(Monitor.ServiceGroup.COUNTER, RESP_PAY.name(),
          Status.RESPONSE_RECEIVED.getTxnManagerStatus());
        log.info("respPayRetry transaction already present in table with status {}. Doing nothing",
          transaction.get().getStatus());
        return;
      }
      log.info("respPayRetry transaction was not present in table. Discarding all the previous data sending fresh request.");

      customMetricService.recordCountMetric(Monitor.ServiceGroup.COUNTER, RESP_PAY.name(), Status.CRON_TIMEOUT.getTxnManagerStatus());
      asyncTxnService.createEntryForRespPay(retryDto.getMsgId(), getTxnIdFromMsgId(retryDto.getMsgId()),
        retryDto.getParams().get(RetryParams.REF_ID), Status.CRON_TIMEOUT, null);
    }else
      log.info("check txn for req pay received for msg id {}",retryDto.getMsgId());

    ReqPay reqPay = JsonUtil.parseJson(retryDto.getParams().get(Constants.REQ_PAY), ReqPay.class);
    //check txn status
    Boolean isRetryReqPay = true;
    try{
      int retryCount;
      if (!retryDto.getParams().containsKey(Constants.ReqPay.CHECK_TXN_RETRY_COUNT))
        retryDto.getParams().put(Constants.ReqPay.CHECK_TXN_RETRY_COUNT,"0");
      retryCount = Integer.parseInt(
        retryDto.getParams().get(Constants.ReqPay.CHECK_TXN_RETRY_COUNT)) + 1;
      log.info("Check Transaction status req pay,retry count {}, msg id {}",retryCount,retryDto.getMsgId());
      retryDto.getParams().put(Constants.ReqPay.CHECK_TXN_RETRY_COUNT,String.valueOf(retryCount));
      if (retryCount <= retryLimit.getCheckTxnStatusReqPayMaxRetry())
        isRetryReqPay = shouldRetryReqPay(reqPay,retryDto);
      else
        isRetryReqPay = false;
    }catch (Exception exception){
      log.error("Error while fetching Transaction status",exception);
    }

    if(!Boolean.TRUE.equals(isRetryReqPay)){
      log.info("Transaction status received successfully, Skipping Req pay retry for msg id {}",retryDto.getMsgId());
    }else {
      if (retryLimit.getReqPayMaxRetry() <= getRetryCountFromMsgId(retryDto.getMsgId())) {
        failureLogger.error("After retrying for {} times, skipping retry of RESP_PAY. Retry Data : {} and ReqPay :{}",
          retryLimit.getReqPayMaxRetry(), JsonUtil.serialiseJson(retryDto), reqPay);
      } else {
        // retry
        kafkaProducer.sendReqPayRetryMsg(reqPay);
      }
    }
  }

  public Boolean shouldRetryReqPay(ReqPay reqPay, RetryDto retryDto) {
    String msgId = retryDto.getMsgId();
    String txnDate = retryDto.getParams().get(RetryParams.TXN_DATE);
    List<TransactionReq> checkTransactionReq = new ArrayList<>();
    checkTransactionReq.add(TransactionReq.builder()
      .txnId(reqPay.getNetcTxnId())
      .acquirerId(reqPay.getAcquirerId())
      .merchantId(reqPay.getPlazaId())
      .txnDate(txnDate)
      .build());
    RespCheckTransactionXml respCheckTransactionXml = checkTransactionService.checkTransactionStatus(checkTransactionReq);
    if (respCheckTransactionXml == null){
      log.error("Error fetching transaction status for ReqPay with msg id {}",msgId);
      return true;
    }
    log.info("Check txn response, {}",JsonUtil.serialiseJson(respCheckTransactionXml));
    //check for error in check txn status
    String readerReadTime = reqPay.getTagReadTime();
    List<RespCheckTransactionXml.StatusXml> statusXmls = respCheckTransactionXml.getCheckTxnTransactionXml()
      .getResponse().getStatusList();
    if (CollectionUtils.isEmpty(statusXmls)) {
      return true;
    }

    RespCheckTransactionXml.StatusXml statusXml = statusXmls.get(0);
    if (!StringUtils.isEmpty(statusXml.getResult()) && statusXml.getResult().equals(RESULT_FAILURE)) {
      //if error code is TRANSACTION COMBINATION ERROR, retry request pay.
      log.error("Error fetching transaction with error code {}", statusXml.getErrCode());
      return true;
    }
    //filter txns with reader read time.
    List<RespCheckTransactionXml.TransactionListXml> transactionListXmls =
      statusXml.getTransactionList().stream()
        .filter(transactionListXml -> transactionListXml.getTxnReaderTime().equals(readerReadTime)).collect(
        Collectors.toList());
    if (transactionListXmls.isEmpty()) {
      log.info("No transaction found with reader read time,{}", readerReadTime);
      return true;
    }
    
    log.info("Fetching Accepted/Deemed Accepted txn list from resp, msgId {}", msgId);
    List<RespCheckTransactionXml.TransactionListXml> acceptedTransactionListXmls = transactionListXmls.stream().
      filter(transactionListXml -> transactionListXml.getTxnStatus().equals(TRANSACTION_ACCEPTED) ||
        transactionListXml.getTxnStatus().equals(TRANSACTION_DEEMED_ACCEPTED)).collect(Collectors.toList());
    if (!acceptedTransactionListXmls.isEmpty()) {
      log.info("Accepted transaction found in response, msgId {}", msgId);
      handleCheckTxnRespAndRetry(acceptedTransactionListXmls.get(0), statusXml.getTxnId(), reqPay, retryDto);
      return false;
    }
    //if there exist declined txn,handle resp
    transactionListXmls.sort(
      Comparator.comparing(RespCheckTransactionXml.TransactionListXml::getTxnReceivedTime).reversed());
    log.info("No accepted transaction in response, Handling declined txn with latest txn time, msgId {}", msgId);
    handleCheckTxnRespAndRetry(transactionListXmls.get(0), statusXml.getTxnId(), reqPay, retryDto);

    return false;
  }

  private void handleCheckTxnRespAndRetry(RespCheckTransactionXml.TransactionListXml transactionListXml,
                                             String netcTxnId,ReqPay reqPay,RetryDto retryDto) {
    String plazaId = reqPay.getPlazaId();
    String msgId = retryDto.getMsgId();
    log.info("Handling Check txn response and retry, msgId {} and transaction List {}", msgId, transactionListXml);
    if (transactionListXml.getTxnStatus().equals(Constants.ReqPay.TXN_PENDING)) {
      int checkTxnRetryCount = 1;
      try {
        checkTxnRetryCount = Integer.parseInt(
          retryDto.getParams().getOrDefault(Constants.ReqPay.CHECK_TXN_RETRY_COUNT, "1"));
      } catch (Exception e) {
        log.error(
          "Error while deserializing additional param for messageId {} : {} ",
          msgId, e.getMessage());
      }
      log.info("Pending txn status received, check txn retry count {}",checkTxnRetryCount);
      customMetricService.recordCountMetric(Monitor.ServiceGroup.COUNTER, REQ_TXN_STATUS.name(), Constants.ReqPay.TXN_PENDING);
      reminderService.addReminder(retryDto, null,
        (checkTxnRetryCount) * timeoutProperties.getRespPayTimeout().longValue());
      return;
    }

    AsyncTransaction request = asyncTxnService.checkAndGetReqPay(msgId);
    String responseCode = transactionListXml.getResponseCode();
    List<String> responseCodeList = Arrays.asList(responseCode.split(","));

    // get list of exception mappings
    List<ErrorCodeMapping> errorCodeMappings = errorCodeMappingRepository.findByHandlerNotNull();
    Map<String, ErrorCodeMapping> retryableErrorCodes = errorCodeMappings.stream()
      .collect(Collectors.toMap(ErrorCodeMapping::getErrorCode, Function.identity()));

    RespPay respPay = new RespPay();
    respPay.setRefId(request.getRefId());
    respPay.setStatus(Status.RESPONSE_RECEIVED);
    respPay.setResult(transactionListXml.getTxnStatus());
    respPay.setNetcTxnId(netcTxnId);
    respPay.setNetcResponseTime(transactionListXml.getTxnReceivedTime());
    respPay.setErrorCodes(transactionListXml.getResponseCode());
    respPay.setSettledPlazaId(plazaId);

    // TPT-5228: Consider 201 error code as success
    if (responseCodeList.contains("201")) {
      log.info("Got 201 error code for MsgId {}, TxnId {}. Sending it as Accepted Transaction.",
        request.getMsgId(), netcTxnId);
      respPay.setResult("ACCEPTED");
      respPay.setErrorCodes(SUCCESS_RESPONSE_CODE);
      respPay.setNetcResponseTime(asyncTxnService.findTxnSuccessfulTimeByRefId(respPay.getRefId()));
    }
    respPayService.handleData(respPay);

    // success
    if (retryableErrorCodes.keySet().containsAll(responseCodeList)) {
      computeRespPayErrorCodes(responseCodeList, retryableErrorCodes,
        msgId, netcTxnId, request.getRefId(), responseCode);
    }

  }

  private void computeRespPayErrorCodes(List<String> responseCodeList, Map<String, ErrorCodeMapping> retryableErrorCodes,
    String msgId, String netcTxnId, String txnRefId, String responseCode) {
    
    List<ErrorCodeMapping> mappings = responseCodeList.stream().map(retryableErrorCodes::get).collect(
      Collectors.toList());
    if (mappings.stream().map(ErrorCodeMapping::getHandler).collect(Collectors.toSet()).size() == 1) {
      HandlerType handlerType = mappings.get(0).getHandler();
      switch (handlerType) {
        case AUTO_RETRY:
          log.info("Got retryable error code: {} for ReqPay msg id: {}", responseCode, msgId);
          asyncTxnService.updateEntry(msgId, Status.CRON_TIMEOUT_AUTO_RETRY_CODE, RESP_PAY);
          if (getRetryCountFromMsgId(msgId) < retryProperties.getReqPayMaxRetry()) {
            ReqPayRetry reqPayRetryDto = new ReqPayRetry();
            reqPayRetryDto.setNetcTxnId(netcTxnId);
            reqPayRetryDto.setTxnReferenceId(txnRefId);
            kafkaProducer.notifyTxnManagerToRetryReqPay(reqPayRetryDto);
          } else {
            failureLogger.info(
              "AUTO_RETRY : max retry reached for this txn. Not sending any more reties. codes:{}, txnId:{}, msgId:{}",
              responseCode, netcTxnId, msgId);
          }
          break;
        case MANUAL_RETRY:
          asyncTxnService.updateEntry(msgId, Status.CRON_TIMEOUT_MANUAL_RETRY_CODE, RESP_PAY);
          failureLogger.info("MANUAL_RETRY: codes {}, txnId: {}, msgId: {}",
            responseCode, netcTxnId, msgId);
          break;
        case AUTO_BLACK_LIST:
          log.info("Got error code which should be BlackListed by Acquirer Engine: msgId: {}, codes {}",
            responseCode, msgId);
      }
    }

  }

  /**
   * FOR: RESP_GET_EXCEPTION_LIST & RESP_QUERY_EXCEPTION_LIST
   * <p>
   * Called by reminder service to notify timeout
   * and retry if necessary.
   * <p>
   * We ignore if we already got the response or else
   * we purge existing data and trigger new request.
   *
   * @param retryDto meta data to identify request and retry operation
   */
  public void exceptionListRetry(RetryDto retryDto, NetcEndpoint endpoint) {
    String msgNum = retryDto.getParams().get(RetryParams.MSG_NUM);
    String totalMsg = retryDto.getParams().get(RetryParams.TOTAL_MSG);
    String uid = retryDto.getParams().getOrDefault(Constants.UID,"");
    boolean testRequestViaAPI = Boolean.parseBoolean(retryDto.getParams().getOrDefault(TEST_REQUEST_VIA_API, "false"));
    Optional<AsyncTransaction> transaction = asyncTxnService.checkIfResponseReceived(retryDto.getApi(), retryDto.getMsgId(), Integer.valueOf(msgNum));
    if (transaction.isPresent()) {
      log.info("exceptionListRetry transaction already present in table with status {}. Doing nothing", transaction.get().getStatus());
      return;
    }

    log.info("exceptionListRetry transaction was not present in table. Discarding all the previous data sending fresh request.");

    // Mark message as timed out
    redisService.timeoutMsgId(retryDto.getMsgId());
    // purge data from redis
    if (endpoint == RESP_GET_EXCEPTION_LIST) {
      redisService.purgeInitData(retryDto.getMsgId());
    } else {
      redisService.purgeDiffData(retryDto.getMsgId());
    }

    String meta = getMetaData(endpoint, uid, retryDto);
    // create entry to record timeout
    asyncTxnService.createEntry(
        AsyncTransaction.builder()
            .msgId(retryDto.getMsgId())
            .api(retryDto.getApi())
            .msgNum(Integer.valueOf(msgNum))
            .totalMsg(Integer.valueOf(totalMsg))
            .status(Status.CRON_TIMEOUT)
            .statusCode("CRON")
            .txnId(getTxnIdFromMsgId(retryDto.getMsgId()))
            .metaData(meta)
            .build()
    );

    // initiate new request
    int retryCount = getRetryCountFromMsgId(retryDto.getMsgId());

    if (RESP_GET_EXCEPTION_LIST.equals(retryDto.getApi())) {
      // Mark the original request as timed out
      if (redisService.isInitCompletionFlagExists()) {
        log.error("Init file is already processed for the day");
        asyncTxnService.updateEntry(retryDto.getMsgId(), Status.RESPONSE_RECEIVED_SFTP, GET_EXCEPTION_LIST);
        return;
      }

      customMetricService.recordCountMetric(Monitor.ServiceGroup.COUNTER, RESP_GET_EXCEPTION_LIST.name(),
        Status.CRON_TIMEOUT.getTxnManagerStatus());
      asyncTxnService.updateEntry(retryDto.getMsgId(), Status.CRON_TIMEOUT, GET_EXCEPTION_LIST);
      if (retryCount <= retryProperties.getReqGetExceptionMaxRetry()) {
        ExceptionMetaInfo additionalParam =
          asyncTxnService.getAdditionalParamByMsgId(
            retryDto.getMsgId(),
            ExceptionMetaInfo.class);
        initService.getExceptionList(retryCount, additionalParam);
      }
      else {
        // Let DIFF flow continue from now on
        redisService.removeInitInProgressFlag();
        failureLogger.error("max retry for get exception reached. data :{}", JsonUtil.serialiseJson(retryDto));
      }
    }
    if (RESP_QUERY_EXCEPTION_LIST.equals(retryDto.getApi())) {
      if(!testRequestViaAPI) {
        Optional<AsyncTransaction> request = asyncTxnService.findByMsgIdAndApi(
          retryDto.getMsgId(), NetcEndpoint.QUERY_EXCEPTION_LIST);
        if(!request.isPresent()) {
          return;
        }
        // TPT-4428 : In case DIFF response didn't come in specified time then just generate Empty DIFF
        tagExceptionService.generateEmptyQueryExceptionData(retryDto.getMsgId(), uid, false,
          request.get().getCreatedAt().toLocalDateTime());
      }
      // Mark the original request as timed out
      asyncTxnService.updateEntry(retryDto.getMsgId(), Status.CRON_TIMEOUT, QUERY_EXCEPTION_LIST);
    }
  }
}
