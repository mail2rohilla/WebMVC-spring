package com.paytm.acquirer.netc.service;

import com.paytm.acquirer.netc.adapter.ExceptionAdapter;
import com.paytm.acquirer.netc.config.properties.RetryProperties;
import com.paytm.acquirer.netc.config.properties.TimeoutProperties;
import com.paytm.acquirer.netc.dto.getException.ReqGetExceptionListXml;
import com.paytm.acquirer.netc.dto.kafka.ExceptionMetaInfo;
import com.paytm.acquirer.netc.dto.retry.RetryDto;
import com.paytm.acquirer.netc.enums.NetcEndpoint;
import com.paytm.acquirer.netc.enums.Status;
import com.paytm.acquirer.netc.service.common.MetadataService;
import com.paytm.acquirer.netc.service.common.NetcClient;
import com.paytm.acquirer.netc.service.common.RedisService;
import com.paytm.acquirer.netc.service.common.SignatureService;
import com.paytm.acquirer.netc.util.Utils;
import com.paytm.acquirer.netc.util.XmlUtil;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;

import static com.paytm.acquirer.netc.adapter.KafkaAdapter.getExceptionKafkaMsg;
import static com.paytm.acquirer.netc.adapter.KafkaAdapter.getExceptionNewRequestKafkaMsg;

@Service
@RequiredArgsConstructor
public class InitService {

  private static final Logger log = LoggerFactory.getLogger(InitService.class);

  private final NetcClient netcClient;
  private final SignatureService signatureService;
  private final MetadataService metadataService;
  private final AsyncTxnService asyncTxnService;
  private final RedisService redisService;
  private final RetryProperties retryProperties;
  private final TimeoutProperties timeoutProperties;
  private final ReminderService reminderService;
  private final PassService passService;

  public void getExceptionList(int retryCount) {
    getExceptionList(retryCount, null);
  }

  public void getExceptionList(int retryCount, ExceptionMetaInfo additionalParams) {
    log.info("getExceptionList called with retry count : {}", retryCount);
    if (retryCount > retryProperties.getReqGetExceptionMaxRetry()) {
      log.error("retry count more then allowed. Dropping request. count : {}", retryCount);
      // Since even last retry was even timeout we should
      // remove INIT progress flag so that DIFF can continue from now on
      redisService.removeInitInProgressFlag();
      return;
    }

    if(retryCount == 0) {
      passService.saveActivePassForInit(additionalParams);
    }

    // create common timeStamp
    String requestTimeStamp = Utils.getFormattedDate();

    ReqGetExceptionListXml request =
      ExceptionAdapter.getExceptionListDto(requestTimeStamp, metadataService);
    request.setHeader(
      metadataService.createXmlHeader(request.getTransaction().getId(),
        requestTimeStamp, null,retryCount + 1));
    request
      .getTransaction()
      .setOriginalTransactionId(metadataService.getOrgTxnId(request.getTransaction().getId()));

    // ask exception-handler ot preload data
    try{
      netcClient.requestExceptionHandlerToPreloadTagsData(request.getHeader().getMessageId());
    }catch (Exception e){
      log.error("[init sync] -> error occurred while trying to preload data at exception handler", e);
    }
    String signedXml = signatureService.signXmlDocument(XmlUtil.serializeXmlDocument(request));

    int statusCode = -1;

    try {
      // Mark INIT as In-Progress so that we don't request DIFF data until INIT flow is done
      redisService.setInitInProgressFlag(request.getHeader().getMessageId());
      log.info("<--- hitting getExceptionList with data : {}", signedXml);
      asyncTxnService.createEntry(asyncTxnService.buildAsyncTxnRecord(request.getHeader().getMessageId(),
        request.getTransaction(), Status.INITIATING_REQUEST, NetcEndpoint.GET_EXCEPTION_LIST,
        "", additionalParams));
      ResponseEntity<Void> responseEntity = netcClient.requestGetExceptionList(signedXml);
      if (responseEntity != null) {
        statusCode = responseEntity.getStatusCodeValue();
      }
      reminderService.addReminder(
        getExceptionKafkaMsg(
          request.getHeader().getMessageId(),
          String.valueOf(1),
          String.valueOf(1),
          String.valueOf(retryCount + 1)),
        null,
        timeoutProperties.getFirstRespGetExceptionTimeout().longValue()
      );
      // record progress

      asyncTxnService.updateEntry(request.getHeader().getMessageId(), Status.REQUEST_OK, Integer.toString(statusCode),
        NetcEndpoint.GET_EXCEPTION_LIST);

    } catch (Exception ex) {
      log.info("error while calling reqGetExceptionList :{}", ex.getLocalizedMessage());
      // record failure status

      if (ex instanceof HttpStatusCodeException) {
        HttpStatusCodeException exception = (HttpStatusCodeException) ex;
        statusCode = exception.getStatusCode().value();
      }

      asyncTxnService.updateEntry(request.getHeader().getMessageId(), Status.REQUEST_FAILED,
        Integer.toString(statusCode), NetcEndpoint.GET_EXCEPTION_LIST);
      //retry after some time using reminder service.

      if (retryCount < retryProperties.getReqGetExceptionMaxRetry()) {
        reminderService.addReminder(
          getExceptionNewRequestKafkaMsg(request.getHeader().getMessageId(), retryCount + 1),
          null,
          timeoutProperties.getReqGetExceptionLocalRetryTimeout().longValue()
        );
        return;
      } else {
        // Remove INIT In-Progress flag so that DIFF flow can continue
        redisService.removeInitInProgressFlag();
        log.error("max local retry reached for get exception list . Please check. data:{}", request);
      }

      throw ex;
    } finally {
      log.info("status code for getExceptionList is :{}", statusCode);
    }
  }

}
