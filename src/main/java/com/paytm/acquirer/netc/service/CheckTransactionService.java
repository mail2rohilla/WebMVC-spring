package com.paytm.acquirer.netc.service;

import com.paytm.acquirer.netc.adapter.CheckTransactionAdapter;
import com.paytm.acquirer.netc.config.properties.RetryProperties;
import com.paytm.acquirer.netc.dto.transactionstatus.ReqCheckTransactionXml;
import com.paytm.acquirer.netc.dto.transactionstatus.RespCheckTransactionXml;
import com.paytm.acquirer.netc.dto.transactionstatus.TransactionReq;
import com.paytm.acquirer.netc.service.common.MetadataService;
import com.paytm.acquirer.netc.service.common.NetcClient;
import com.paytm.acquirer.netc.service.common.SignatureService;
import com.paytm.acquirer.netc.util.Constants;
import com.paytm.acquirer.netc.util.Utils;
import com.paytm.acquirer.netc.util.XmlUtil;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.List;

import static com.paytm.acquirer.netc.util.Constants.RESULT_FAILURE;

@Service
@RequiredArgsConstructor
public class CheckTransactionService {
  private static final Logger log = LoggerFactory.getLogger(CheckTransactionService.class);

  private final MetadataService metadataService;
  private final SignatureService signatureService;
  private final NetcClient netcClient;
  private final TimeService timeService;
  private final RetryProperties retryProperties;

  private RespCheckTransactionXml checkTransaction(List<TransactionReq> transactionReqList) {
    // create common time to be used across a request
    String requestTime = Utils.getFormattedDate();

    ReqCheckTransactionXml request =
      CheckTransactionAdapter.convertTransactionCheckToXmlDto(transactionReqList, metadataService, requestTime);
    request.setHeader(
      metadataService.createXmlHeader(request.getTransaction().getId(),
        requestTime, null, 0));
    request
      .getTransaction()
      .setOriginalTransactionId(metadataService.getOrgTxnId(request.getTransaction().getId()));
    String signedXml = signatureService.signXmlDocument(XmlUtil.serializeXmlDocument(request));

    RespCheckTransactionXml respCheckTransactionXml = null;
    try {
      log.info("Signed Request XML for Check Transaction : {} ", signedXml);
      ResponseEntity<String> responseEntity = netcClient.requestTransactionCheck(signedXml);
      log.info("Response from check Transaction :{} , status {}",
        responseEntity, responseEntity.getStatusCode().value());

      if (responseEntity.getStatusCode().is2xxSuccessful()) {
        respCheckTransactionXml =
          XmlUtil.deserializeXmlDocument(responseEntity.getBody(), RespCheckTransactionXml.class);
      }
    } catch (Exception exception) {
      log.error("error while calling check Transaction Status, exception :{} ", exception.getLocalizedMessage());

      if (exception instanceof HttpStatusCodeException) {
        HttpStatusCodeException ex = (HttpStatusCodeException) exception;
        log.error("Check txn status response returned with status code {}",ex.getStatusCode().value());
      }
    }

    return respCheckTransactionXml;
  }

  public RespCheckTransactionXml checkTransactionStatus(List<TransactionReq> transactionReqList){
    RespCheckTransactionXml respCheckTransactionXml = checkTransaction(transactionReqList);

    int retryCount = 0;
    while (retryCount++ < retryProperties.getCheckTxnStatusMaxRetry()) {
      if(respCheckTransactionXml == null)
        return null;

      if (respCheckTransactionXml.getCheckTxnTransactionXml()
        .getResponse().getResult().equals(RESULT_FAILURE)){

        if(respCheckTransactionXml.getCheckTxnTransactionXml()
          .getResponse().getResponseCode().equals(Constants.TIME_SYNC_ERROR_CODE)){
          timeService.syncTimeWithNetc();
          respCheckTransactionXml = checkTransaction(transactionReqList);
        }else {
          log.info("Check Transaction Status Response return result {}, with error code {}",
            respCheckTransactionXml.getCheckTxnTransactionXml().getResponse().getResult(),
            respCheckTransactionXml.getCheckTxnTransactionXml().getResponse().getResponseCode());
          return respCheckTransactionXml;
        }
      }
    }

    return respCheckTransactionXml;
  }
}