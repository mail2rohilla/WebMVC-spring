package com.paytm.acquirer.netc.controller.temp;

import com.google.common.collect.Iterables;
import com.paytm.acquirer.netc.db.entities.AsyncTransaction;
import com.paytm.acquirer.netc.db.repositories.slave.AsyncTransactionSlaveRepository;
import com.paytm.acquirer.netc.dto.common.TransactionStatus;
import com.paytm.acquirer.netc.dto.pay.ReqPay;
import com.paytm.acquirer.netc.dto.pay.RespPay;
import com.paytm.acquirer.netc.dto.transactionstatus.ReqCheckTransaction;
import com.paytm.acquirer.netc.dto.transactionstatus.RespCheckTransactionXml;
import com.paytm.acquirer.netc.enums.NetcEndpoint;
import com.paytm.acquirer.netc.enums.Status;
import com.paytm.acquirer.netc.kafka.producer.KafkaProducer;
import com.paytm.acquirer.netc.service.CheckTransactionService;
import com.paytm.acquirer.netc.service.ReqPayService;
import com.paytm.acquirer.netc.service.common.EfkonSignatureService;
import com.paytm.acquirer.netc.service.common.RedisService;
import com.paytm.acquirer.netc.service.common.SignatureService;
import com.paytm.acquirer.netc.util.Constants;
import com.paytm.acquirer.netc.util.JsonUtil;
import com.paytm.transport.kafka.TransportKafkaTemplate;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import com.paytm.transport.service.HikariMetricService;
import com.paytm.transport.util.DynamicPropertyUtil;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.paytm.acquirer.netc.util.Utils.dateTimeFormatter;
import static org.springframework.util.MimeTypeUtils.TEXT_PLAIN_VALUE;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "temp")
@Hidden
public class TempController {

  private static final Logger log = LoggerFactory.getLogger(TempController.class);

  private final ReqPayService reqPayService;
  private final SignatureService signatureService;
  private final TransportKafkaTemplate<Object> kafkaTemplate;
  private final KafkaProducer kafkaProducer;
  private final RedisService redisService;
  private final AsyncTransactionSlaveRepository asyncTransactionMasterRepository;
  private final CheckTransactionService checkTransactionService;
  private final EfkonSignatureService efkonSignatureService;
  private final HikariMetricService hikariMetricService;
  private final Environment environment;

  @Value("${kafka.topic.reqPay}")
  private String topic;

  @Value("${kafka.topic.respPay}")
  private String respPayTopic;

  @PostMapping("reqPay")
  public ResponseEntity<String> reqPay(
    @RequestBody ReqPay reqPay,
    @RequestParam(value = "kafka", required = false, defaultValue = "false") Boolean pushToKafka) {

    if(!environment.acceptsProfiles(Profiles.of("dev"))) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    if (Boolean.TRUE.equals(pushToKafka)) {
      kafkaTemplate.sendSync(topic, reqPay);
    } else {
      reqPayService.requestPayment(reqPay);
    }
    return ResponseEntity.ok().body("Success");
  }


  @PostMapping("async/reqPay")
  public String asyncReqPay(
    @RequestBody ReqPay reqPay,
    @RequestParam(value = "numReq", required = false, defaultValue = "1") int numReq,
    @RequestParam(value = "delay",required = false, defaultValue = "0") long delay) throws InterruptedException {
    boolean isHardRetry = reqPay.isHardRetry();
    for (int i = 0; i < numReq; i++) {
      Thread.sleep(delay);
      reqPay.setHardRetry(isHardRetry);
      reqPayService.asyncRequestPayment(reqPay);
    }
    return "Success";
  }

  @PostMapping(value = "sign", produces = TEXT_PLAIN_VALUE, consumes = TEXT_PLAIN_VALUE)
  public String signRequest(@RequestBody String request) {
    String result = signatureService.signXmlDocument(request);
    return result.replace("&#13;", "");
  }

  @PostMapping(value = "efkon_sign", produces = TEXT_PLAIN_VALUE, consumes = TEXT_PLAIN_VALUE)
  public String signEfkonRequest(@RequestBody String request) {

    String result = efkonSignatureService.signXmlDocument(request);
    return result.replace("&#13;", "");
  }

  @GetMapping(value = "decode")
  public String issuerId(@RequestParam("tagId") String tagId) {
    String binaryTagId = new BigInteger(tagId, 16).toString(2);
    // prepend zeros
    binaryTagId = String.format("%96s", binaryTagId).replace(' ', '0');
    String binaryIssuerId = binaryTagId.substring(43, 63);
    return new BigInteger(binaryIssuerId, 2).toString(10);
  }

  @GetMapping("produce")
  public String reqPay(@RequestParam("n") Integer totalMsgs) {
    String string =
        "{\n"
            + "  \"txnReferenceId\": \"21664\",\n"
            + "  \"txnTime\": \"2019-07-29T19:00:00\",\n"
            + "  \"txnType\": \"DEBIT\",\n"
            + "  \"plazaId\": 900900,\n"
            + "  \"plazaName\": \"DummyPlaza\",\n"
            + "  \"plazaGeoCode\": \"51.51,51.51\",\n"
            + "  \"plazaType\": \"NATIONAL\",\n"
            + "  \"laneId\": \"L04\",\n"
            + "  \"laneDirection\": \"N\",\n"
            + "  \"tagReadTime\": \"2019-07-29T18:31:13\",\n"
            + "  \"tagId\": \"34161FA82032D69866000880\",\n"
            + "  \"avc\": \"VC10\",\n"
            + "  \"wim\": \"1\",\n"
            + "  \"amount\": \"75.0\",\n"
            + "  \"acquirerId\": \"720612\",\n"
            + "  \"vehicleDetails\": {\n"
            + "    \"tagId\": \"34161FA82032D69866000880\",\n"
            + "    \"regNumber\": \"MH02BY2316\",\n"
            + "    \"tid\": \"34161FA82032D69866000880\",\n"
            + "    \"vehicleClass\": \"VC5\",\n"
            + "    \"tagStatus\": \"A\",\n"
            + "    \"issueDate\": \"2017-05-23\",\n"
            + "    \"exceptionCodes\": [\n"
            + "      \"00\"\n"
            + "    ],\n"
            + "    \"bankId\": \"617292\",\n"
            + "    \"commercialVehicle\": \"F\"\n"
            + "  }\n"
            + "}";
    int maxMsg = 100;
    ReqPay reqPay = JsonUtil.parseJson(string, ReqPay.class);
    for (int i = 0; i < Math.min(totalMsgs,maxMsg); i++) {
      assert reqPay != null;
      reqPay.setTxnReferenceId("sample+" + i);
      kafkaTemplate.sendSync(topic, reqPay);
    }
    return "success";
  }

  @PostMapping("sendAcceptedTxnStatus")
  public void sendAcceptedTxnStatus(@RequestBody List<RespPay> respPays) {
    respPays.forEach(kafkaProducer::send);
  }

  @PostMapping("removeInitCompletionFlag")
  public void removeInitCompletionFlag() {
    redisService.removeInitCompletionFlag();
  }

  @PostMapping("setInitCompletionFlag")
  public void setInitCompletionFlag() {
    redisService.setInitCompletionFlag(Boolean.TRUE);
  }

  @PostMapping("removeInitInProgress")
  public void removeInitInProgress() {
    redisService.removeInitInProgressFlag();
  }

  @PostMapping("removeKey")
  public void removeKey(@RequestParam("key") String key) {
    redisService.removeKey(key);
  }

  @PostMapping("markTxnStatus")
  public ResponseEntity<String> markTxnStatus(@RequestBody TransactionStatus transactionStatus) {
    int maxTxnAllowed = DynamicPropertyUtil.getIntPropertyValue("ACCEPTED_TXN_MAX_SIZE", 1000);
    if (Objects.isNull(transactionStatus.getRefIds()) || transactionStatus.getRefIds().isEmpty())
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    if (transactionStatus.getRefIds().size() > maxTxnAllowed){
      log.info("Max transactions allowed is {}, got {}",maxTxnAllowed,transactionStatus.getRefIds());
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }

    int size = DynamicPropertyUtil.getIntPropertyValue("ACCEPTED_TXN_SIZE", 100);
    Iterables.partition(transactionStatus.getRefIds(), size).forEach(refIds -> {

      Map<String,
        List<AsyncTransaction>> theMap = asyncTransactionMasterRepository.findByApiAndRefIdInOrderByIdAsc(
        NetcEndpoint.REQ_PAY, transactionStatus.getRefIds())
        .stream().collect(Collectors.groupingBy(AsyncTransaction::getTxnId));

      List<AsyncTransaction> asyncResponseTransactions = asyncTransactionMasterRepository.
        findByApiAndStatusInAndRefIdInOrderByIdAsc(NetcEndpoint.RESP_PAY,
          Collections.singletonList(Status.RESPONSE_RECEIVED),
          transactionStatus.getRefIds());

      Map<String, AsyncTransaction> txnResp = asyncResponseTransactions.stream().collect(
        Collectors.toMap(AsyncTransaction::getTxnId,
          Function.identity(),
          (t1, t2) -> Constants.SUCCESS_RESPONSE_CODE_LIST.contains(t1.getStatusCode()) ? t1 : t2));

      for (Map.Entry<String, List<AsyncTransaction>> entry : theMap.entrySet()) {
        AsyncTransaction asyncTransaction = entry.getValue().get(0);
        log.info("Adding resp pay for the transaction {} msgId {}", asyncTransaction.getTxnId(),
          asyncTransaction.getMsgId());

        RespPay respPay = new RespPay();
        respPay.setRefId(asyncTransaction.getRefId());
        respPay.setResult("ACCEPTED");
        respPay.setErrorCodes("000");
        respPay.setNetcResponseTime(asyncTransaction.getCreatedAt().toLocalDateTime().format(dateTimeFormatter));
        respPay.setNetcTxnId(asyncTransaction.getTxnId());
        respPay.setStatus(Status.RESPONSE_RECEIVED);

        if (Objects.nonNull(txnResp.get(asyncTransaction.getTxnId()))) {
          AsyncTransaction asyncRespReceivedTransaction = txnResp.get(asyncTransaction.getTxnId());
          respPay.setNetcResponseTime(
            asyncRespReceivedTransaction.getUpdatedAt().toLocalDateTime().format(dateTimeFormatter));
        }
        kafkaTemplate.sendSync(respPayTopic, respPay);
      }
    });
    return ResponseEntity.status(HttpStatus.OK).body("SUCCESS");
  }

  @PostMapping("reqChkTxn")
  public RespCheckTransactionXml checkTransactionStatus(@RequestBody ReqCheckTransaction reqTransactionCheck) {
    return checkTransactionService.checkTransactionStatus(reqTransactionCheck.getTransactions());
  }

  @GetMapping("reprocess-job")
  public void reprocessJob(@RequestParam("jobId") Integer jobId) {
    Map<String, Integer> map = new HashMap<>();
    map.put("id", jobId);
    kafkaTemplate.sendSync("acquirer_reporting_file_centre_job", map);
  }

  @GetMapping("change-circuit-state")
  public void changeCircuitState(@RequestParam("state") Integer state) {
    Integer openState = 1;
    if (openState.equals(state)) {
      redisService.setCircuitOpenKey();
    } else {
      redisService.removeCircuitOpenKey();
    }
  }

  @GetMapping("v1/pushHikariMetric")
  public void pushHikariMetric() {
    hikariMetricService.sendMetrics();
  }
}
