package com.paytm.acquirer.netc.service;

import com.paytm.acquirer.netc.adapter.PayAdapter;
import com.paytm.acquirer.netc.config.properties.RetryProperties;
import com.paytm.acquirer.netc.config.properties.TimeoutProperties;
import com.paytm.acquirer.netc.db.entities.AsyncTransaction;
import com.paytm.acquirer.netc.dto.common.VehicleDetails;
import com.paytm.acquirer.netc.dto.pay.ReqPay;
import com.paytm.acquirer.netc.dto.pay.ReqPayXml;
import com.paytm.acquirer.netc.dto.pay.RespPay;
import com.paytm.acquirer.netc.dto.retry.ReqPayRetry;
import com.paytm.acquirer.netc.dto.retry.RetryDto;
import com.paytm.acquirer.netc.enums.NetcEndpoint;
import com.paytm.acquirer.netc.enums.RetrialType;
import com.paytm.acquirer.netc.enums.Status;
import com.paytm.acquirer.netc.kafka.producer.KafkaProducer;
import com.paytm.acquirer.netc.service.common.MetadataService;
import com.paytm.acquirer.netc.service.common.SignatureService;
import com.paytm.acquirer.netc.service.retry.WrappedNetcClient;
import com.paytm.acquirer.netc.util.*;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import com.paytm.transport.service.TollIdMappingService;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.paytm.acquirer.netc.adapter.KafkaAdapter.createKafkaMsgForReqPay;
import static com.paytm.acquirer.netc.adapter.PayAdapter.issuerId;

@Service
@RequiredArgsConstructor
public class ReqPayService {
  private static final Logger log = LoggerFactory.getLogger(ReqPayService.class);

  private final MetadataService metadataService;
  private final SignatureService signatureService;
  private final KafkaProducer producer;
  private final AsyncTxnService asyncTxnService;
  private final WrappedNetcClient retryWrapperService;
  private final KafkaProducer kafkaProducer;
  private final TimeoutProperties timeoutProperties;
  private final RetryProperties retryProperties;
  private final FactoryMethodService factoryMethodService;
  private final ReminderService reminderService;
  private final TollIdMappingService tollIdMappingService;

  @Value("${request.retry.error-codes}")
  private String errorCodes;

  @Value("${tas.npci.time-diff}")
  private Long tasNpciTimeDiff;

  private List<Integer> parsedErrorCodes;

  // TODO: log in different file, removing existing setup due to trans-commons
  private static Logger failureLogger = LoggerFactory.getLogger(ReqPayService.class);

  @PostConstruct
  private void init() {
    parsedErrorCodes = Arrays.stream(errorCodes.split(",")).map(Integer::parseInt).collect(Collectors.toList());
  }

  @Async("exception-list-executor")
  public void asyncRequestPayment(ReqPay reqPay) {
     requestPayment(reqPay);
  }

  public void requestPayment(ReqPay reqPay) {
    // create common time to be used across a request
    String requestTime = Utils.getFormattedDate(tasNpciTimeDiff);

    if (reqPay.getBankId() == null && reqPay.getVehicleDetails() != null) {
      reqPay.setBankId(reqPay.getVehicleDetails().getBankId());
    }
    // TPT-4775: If bankId is not available then extract it from TagId
    if (!StringUtils.hasLength(reqPay.getBankId())) {
      reqPay.setBankId(issuerId(reqPay.getTagId()));
    }

    if (reqPay.getVehicleDetails() == null) {
      VehicleDetails details = PayAdapter.createVehicleDetailsForUnRegisteredTag(reqPay);
      log.info("vehicle details was empty. Created new vehicle details : {}", details);
      reqPay.setVehicleDetails(details);
    }
    //update plaza id in req pay
    updateReqPay(reqPay);

    Integer retryCount = getRetryCountAndSetNetcTxnId(reqPay);
    if (retryCount == null) return;
    IReqPayService iReqPayService = factoryMethodService.getInstance(reqPay);
    ReqPayXml request = iReqPayService.convertReqPayToXml(reqPay, requestTime, metadataService);
    request.setHeader(metadataService.createXmlHeader(request.getTransaction().getId(), requestTime, null, retryCount));
    reqPay.setHardRetry(false);
    String signedXml = signatureService.signXmlDocument(XmlUtil.serializeXmlDocument(request));

    try {
      String meta = JsonUtil.serialiseJson(Collections.singletonMap(Constants.PLAZA_ID,reqPay.getPlazaId()));
      asyncTxnService.createEntry(
          buildAsyncTxnRecord(request, Status.INITIATING_REQUEST, "", reqPay.getTxnReferenceId(),retryCount,meta));
    } catch(DataIntegrityViolationException exception) {
      log.warn("Already initiated the req pay for the transaction with id {}",reqPay.getTxnReferenceId());
      return;
    }
    int code = retryWrapperService.reqPay(signedXml);
    log.info("response code for reqPay is {}", code);
    if (code == 202) {
      // record progress and send status to kafka . already cherry picked.
      producer.send(generateKafkaResponse(reqPay.getTxnReferenceId(), reqPay.getNetcTxnId(), Status.REQUEST_OK,reqPay.getPlazaId()));
      asyncTxnService.updateEntry(request.getHeader().getMessageId(), Status.REQUEST_OK, String.valueOf(code),
          NetcEndpoint.REQ_PAY);
      reminderService.addReminder(createKafkaMsgForReqPay(request, reqPay, RetrialType.NORMAL_RETRY),
        request.getHeader().getMessageId(),(retryCount + 1) * timeoutProperties.getRespPayTimeout().longValue());
    } else {
      producer.send(generateKafkaResponse(reqPay.getTxnReferenceId(), reqPay.getNetcTxnId(), Status.REQUEST_FAILED,reqPay.getPlazaId()));
      asyncTxnService.updateEntry(request.getHeader().getMessageId(), Status.REQUEST_FAILED, String.valueOf(code),
          NetcEndpoint.REQ_PAY);
      if ((parsedErrorCodes.contains(code) || HttpStatus.valueOf(code).is5xxServerError())
          && reqPay.getLocalRetry() < retryProperties.getReqPayLocalMaxRetry()) {
        reqPay.setLocalRetry(reqPay.getLocalRetry() + 1);
        log.info("sending to reminder service. code:{} attempts:{}", code, reqPay.getLocalRetry());
        reminderService.addReminder(createKafkaMsgForReqPay(request, reqPay, RetrialType.CIRCUIT_BREAKER_RETRY),
          null,reqPay.getLocalRetry() * timeoutProperties.getRespPayTimeout().longValue());
      } else {
        failureLogger.error("failed to hit request pay msgId:{} with data:{}", request.getHeader().getMessageId(), JsonUtil.serialiseJson(reqPay));
      }
    }

  }

  private Integer getRetryCountAndSetNetcTxnId(ReqPay reqPay) {
    int retryCount = 0;
    // * try to find if an existing netcTxnId exists for this Ref ID
    Optional<AsyncTransaction> existingTxn = asyncTxnService.findReqPayByRefId(reqPay.getTxnReferenceId());

    if (existingTxn.isPresent()) {
      retryCount = MetadataService.getRetryCountFromMsgId(existingTxn.get().getMsgId()) + 1;
      if(!reqPay.isHardRetry() && retryCount > retryProperties.getReqPayMaxRetry()) {
        log.warn("Max retry limit ({}) reached for Txn Id {}. Skipping it's processing.",
            retryProperties.getReqPayMaxRetry(), existingTxn.get().getTxnId());
        return null;
      }
      log.info("Using existing txnId {} for Ref Id {}", existingTxn.get().getTxnId(), reqPay.getTxnReferenceId());
      reqPay.setNetcTxnId(existingTxn.get().getTxnId());
    }
    else {
      // * generate new netcTxnId
      reqPay.setNetcTxnId(metadataService.getTxnId(NetcEndpoint.REQ_PAY));
      log.info("fresh request came in : {}", reqPay.getNetcTxnId());
    }
    return retryCount;
  }

  private AsyncTransaction buildAsyncTxnRecord(ReqPayXml request, Status status, String statusCode, String refId,Integer retryCount,String meta) {
    return AsyncTransaction.builder()
        .msgId(request.getHeader().getMessageId())
        .refId(refId)
        .status(status)
        .api(NetcEndpoint.REQ_PAY)
        .statusCode(statusCode)
        .msgNum(1)
        .totalMsg(1)
        .txnId(request.getTransaction().getId())
        .retryCount(retryCount)
        .metaData(meta)
        .build();
  }

  private RespPay generateKafkaResponse(String refId, String txnId, Status status,String plazaId) {
    RespPay response = new RespPay();
    response.setRefId(refId);
    response.setNetcTxnId(txnId);
    response.setStatus(status);
    response.setRequestTime(asyncTxnService.getRequestTime(refId));
    response.setSettledPlazaId(plazaId);
    return response;
  }

  public void updateReqPay(ReqPay reqPay){
      reqPay.setPlazaId(tollIdMappingService.getNewMapping(reqPay.getPlazaId()));
  }

  public void handleCircuitBreakerReqPay(RetryDto retryDto){
    ReqPay reqPay = JsonUtil.parseJson(retryDto.getParams().get(Constants.REQ_PAY), ReqPay.class);
    log.info("Got circuit breaker retry for req pay, Sending to req pay queue. {}",
      retryDto.getParams().get(Constants.REQ_PAY));
    kafkaProducer.sendReqPayRetryMsg(reqPay);
  }
  
  public void handleAutoRetryableCode(RetryDto data) {
    log.info("Got AutoRetryAbleCode retry for req pay, Sending to queue");
    
    ReqPayRetry reqPayRetryDto = new ReqPayRetry();
    reqPayRetryDto.setNetcTxnId(data.getParams().get(Constants.RetryParams.NETC_TXN_ID));
    reqPayRetryDto.setTxnReferenceId(data.getParams().get(Constants.RetryParams.REF_ID));
    kafkaProducer.notifyTxnManagerToRetryReqPay(reqPayRetryDto);
  }
}
