package com.paytm.acquirer.netc.service;

import com.paytm.acquirer.netc.db.entities.AsyncTransaction;
import com.paytm.acquirer.netc.db.repositories.master.AsyncTransactionMasterRepository;
import com.paytm.acquirer.netc.dto.common.TransactionXml;
import com.paytm.acquirer.netc.enums.DiffStrategy;
import com.paytm.acquirer.netc.enums.NetcEndpoint;
import com.paytm.acquirer.netc.enums.Status;
import com.paytm.acquirer.netc.exception.NetcEngineException;
import com.paytm.acquirer.netc.service.common.RedisService;
import com.paytm.acquirer.netc.util.Constants;
import com.paytm.acquirer.netc.util.JsonUtil;
import com.paytm.acquirer.netc.util.SFTPUtil;
import com.paytm.acquirer.netc.util.Utils;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.paytm.acquirer.netc.enums.NetcEndpoint.*;

@Service
@RequiredArgsConstructor
public class AsyncTxnService {
  private static final Logger log = LoggerFactory.getLogger(AsyncTxnService.class);

  private final AsyncTransactionMasterRepository asyncTransactionMasterRepository;
  private final RedisService redisService;

  @Value("${netc.diff-overlap-seconds:0}")
  private long diffOverlapSeconds;

  public void createEntry(AsyncTransaction transaction) {
    //unique: api,ref_id,retry_count
    asyncTransactionMasterRepository.save(transaction);
    log.info("Creating AsyncTxn entry {}", transaction);
  }

  public void createEntryForRespPay(String msgId, String txnId, String refId, Status status, String statusCodes) {
    AsyncTransaction transaction = AsyncTransaction.builder()
        .api(NetcEndpoint.RESP_PAY)
        .msgId(msgId)
        .msgNum(1)
        .totalMsg(1)
        .status(status)
        .statusCode(statusCodes)
        .txnId(txnId)
        .refId(refId)
        .build();

    asyncTransactionMasterRepository.save(transaction);
  }

  public void updateEntry(String msgId, Status status, NetcEndpoint endpoint) {
    updateEntry(msgId, status, null, endpoint);
  }

  public void updateEntry(String msgId, Status status, String statusCode, NetcEndpoint endpoint) {
    Optional<AsyncTransaction> searchResult = asyncTransactionMasterRepository.findByMsgIdAndApi(msgId, endpoint);

    if (searchResult.isPresent()) {
      AsyncTransaction result = searchResult.get();
      result.setStatus(status);
      if (statusCode != null) result.setStatusCode(statusCode);
      asyncTransactionMasterRepository.save(result);
      log.info("Updated AsyncTxn {}", result);
    } else {
      log.error("transaction with msgId:{} not found database.", msgId);
      throw new NetcEngineException("Async transaction not updated with msgId: " + msgId, null);
    }
  }

  public void markFetchExceptionSuccessful(String msgId, Boolean isSuccess) {
    if (Objects.equals(msgId, SFTPUtil.getMessageId())) {
      log.info("SFTP INIT processing completed for msgId {}", msgId);
      redisService.removeInitInProgressFlag();
      redisService.removeInitSFTPFlag();
      return;
    }
    Optional<AsyncTransaction> searchResult = asyncTransactionMasterRepository.findFirstByMsgIdAndApiIn(msgId,
            EnumSet.of(GET_EXCEPTION_LIST, QUERY_EXCEPTION_LIST));

    if (searchResult.isPresent()) {
      AsyncTransaction result = searchResult.get();
      if (result.getApi() == GET_EXCEPTION_LIST) {
        redisService.removeInitInProgressFlag();
        redisService.removeInitSFTPFlag();
      }
      if (!EnumSet.of(Status.CRON_TIMEOUT, Status.REQUEST_FAILED).contains(result.getStatus())) {
        result.setStatus(Boolean.TRUE.equals(isSuccess) ? Status.RESPONSE_RECEIVED : Status.EXTERNAL_FAILURE);
        asyncTransactionMasterRepository.save(result);
      }
    }
    else {
      log.info("Exception successful for Diff file via SFTP with msgId: {}", msgId);
    }
  }

  public Optional<AsyncTransaction> checkIfResponseReceived(NetcEndpoint api, String msgId, Integer msgNum) {
    return asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(msgId, api, msgNum);
  }

  public <T> T getAdditionalParamByMsgId(String msgId, Class<T> returnType) {
    Optional<AsyncTransaction> searchResult =
        asyncTransactionMasterRepository.findFirstByMsgIdAndApiOrderByCreatedAtDesc(msgId, NetcEndpoint.GET_EXCEPTION_LIST);

    if (searchResult.isPresent() && searchResult.get().getMetaData() != null) {
      String meta = searchResult.get().getMetaData();
      try {
        return JsonUtil.parseJson(meta, returnType);
      } catch (Exception e) {
        log.error("Error while parsing meta info msg id {} : {} ", msgId, e.getMessage());
      }
    }
    return null;
  }

  public Optional<AsyncTransaction> findReqPayByRefId(String txnReferenceId) {
    return asyncTransactionMasterRepository.findFirstByApiAndRefIdOrderByIdDesc(REQ_PAY, txnReferenceId);
  }

  @Retryable(value = {IllegalStateException.class},
      maxAttemptsExpression = "#{${resp-pay.wait.max-attempts}}",
      backoff = @Backoff(
          delayExpression = "#{${resp-pay.wait.delay-ms}}",
          multiplierExpression = "#{${resp-pay.wait.multiplier}}",
          maxDelayExpression = "#{${resp-pay.wait.max-delay-ms}}"))
  public AsyncTransaction checkAndGetReqPay(String msgId) {
    Optional<AsyncTransaction> txn =
        asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(msgId, NetcEndpoint.REQ_PAY, 1);
    AsyncTransaction asyncTransaction =
        txn.orElseThrow(() -> new NetcEngineException(HttpStatus.BAD_REQUEST,
          "Got response callback for unknown msgId " + msgId));

    if (asyncTransaction.getStatus() == Status.REQUEST_FAILED) {
      throw new IllegalStateException("Request MsgId: " + asyncTransaction.getMsgId() + " is in request failed state.");
    }
    return asyncTransaction;
  }

  public String findTxnSuccessfulTimeByRefId(String refId) {
    List<AsyncTransaction> responseTxn =
        asyncTransactionMasterRepository.findByApiAndStatusInAndRefIdInOrderByIdAsc(RESP_PAY,
            Collections.singletonList(Status.RESPONSE_RECEIVED), Collections.singleton(refId))
        .stream().filter(respTxn -> Constants.SUCCESS_RESPONSE_CODE_LIST.contains(respTxn.getStatusCode()))
        .collect(Collectors.toList());
    if (!CollectionUtils.isEmpty(responseTxn)) {
      return responseTxn.get(0).getCreatedAt().toLocalDateTime().format(Utils.dateTimeFormatter);
    }
    List<AsyncTransaction> firstRequestTxn =
        asyncTransactionMasterRepository.findByApiAndRefIdInOrderByIdAsc(REQ_PAY, Collections.singleton(refId));
    if (!CollectionUtils.isEmpty(firstRequestTxn)) {
      return firstRequestTxn.get(0).getCreatedAt().toLocalDateTime().format(Utils.dateTimeFormatter);
    }
    return LocalDateTime.now().format(Utils.dateTimeFormatter);
  }

  public String getRequestTime(String refId, String responseTime) {
    Optional<AsyncTransaction> primaryRequest =
        asyncTransactionMasterRepository.findFirstByApiAndRefIdAndCreatedAtLessThanEqualOrderByIdDesc(NetcEndpoint.REQ_PAY,
            refId, Timestamp.valueOf(Utils.getLocalDateTime(responseTime)));
    return primaryRequest.map(t -> t.getCreatedAt().toLocalDateTime()
        .format(Utils.dateTimeFormatter)).orElse(responseTime);
  }

  public String getRequestTime(String refId) {
    return getRequestTime(refId, findTxnSuccessfulTimeByRefId(refId));
  }

  public Optional<AsyncTransaction> findByMsgIdAndApi(String msgId, NetcEndpoint endpoint) {
    return asyncTransactionMasterRepository.findByMsgIdAndApi(msgId, endpoint);
  }

  public <T> AsyncTransaction buildAsyncTxnRecord(String messageId, TransactionXml transaction,
    Status status, NetcEndpoint endpoint, String statusCode, T additionalParams) {
    String meta = null;
    if (additionalParams != null) {
      try {
        meta = JsonUtil.serialiseJson(additionalParams);
      }
      catch (Exception e) {
        log.error(
          "Error while serializing additional param {} for messageId {} : {} ", e,
          additionalParams, messageId, e.getMessage());
      }
    }

    return AsyncTransaction.builder()
      .msgId(messageId)
      .status(status)
      .api(endpoint)
      .statusCode(statusCode)
      .txnId(transaction.getId())
      .msgNum(1)
      .totalMsg(1)
      .metaData(meta)
      .build();
  }

}
