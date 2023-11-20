package com.paytm.acquirer.netc.controller;

import com.paytm.acquirer.netc.service.temp.DelegationService;
import com.paytm.acquirer.netc.dto.getException.RespGetExceptionListXml;
import com.paytm.acquirer.netc.dto.pay.RespPayXml;
import com.paytm.acquirer.netc.service.common.SignatureService;
import com.paytm.acquirer.netc.service.retry.RetryResponseService;
import com.paytm.acquirer.netc.util.XmlUtil;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import com.paytm.transport.metrics.Monitor;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.LocalDateTime;

import static com.paytm.transport.metrics.Monitor.ServiceGroup.API_IN;
import static org.springframework.util.MimeTypeUtils.APPLICATION_XML_VALUE;
import static org.springframework.util.MimeTypeUtils.TEXT_PLAIN_VALUE;

@RestController
@RequiredArgsConstructor
@OpenAPIDefinition(info =
@Info(
  title = "NETC Engine",
  version = "1.0",
  description = "This service is responsible for interacting with NETC APIs.",
  contact = @Contact(name = "Paytm Transport Gurgaon", email = "transportation.tech@paytm.com "))
)
@Tag(name = "Callback Controller", description = "This controller is used to handle all callback apis from NPCI.")
public class CallbackController {
  private static final Logger log = LoggerFactory.getLogger(CallbackController.class);

  private final RetryResponseService retryResponseService;
  private final DelegationService delegationService;
  private final SignatureService signatureService;

  @Value("${init-diff-sync.ttl-in-seconds}")
  private int initDiffTtlInSecond;

  @Value("${netc.delegation.active}")
  private Boolean delegation;

  @Value("${log-sample-value}")
  private int samplingValue;

  @PostMapping(value = "responsePayService", produces = "*/*", consumes = TEXT_PLAIN_VALUE)
  @Monitor(name = "respPay", metricGroup = API_IN)
  @Operation(summary = "Callback api for response pay ", description = "Used to receive response of a transaction after processing at NPCI.")
  @ApiResponse(
    responseCode = "202",
    description = "Accepted",
    content = @Content(
      mediaType = TEXT_PLAIN_VALUE)
  )
  @ApiResponse(
    responseCode = "4xx",
    description = "Failed in api validation",
    content = @Content(
      mediaType = TEXT_PLAIN_VALUE)
  )
  @ApiResponse(
    responseCode = "5xx",
    description = "Service Unavailable Or Internal Server Error",
    content = @Content(
      mediaType = TEXT_PLAIN_VALUE)
  )
  public ResponseEntity<String> respPay(@Parameter(description = "XML request body with response digitally signed by the caller (NETC)") @RequestBody String request,
                                        @Parameter(in = ParameterIn.HEADER, description = "Media type of the request") @Header("accept") String accepts) {
    if (Boolean.TRUE.equals(delegation)) delegationService.reqPay(request);
    //removing xml header. was giving error
    String sanitizedRequest = request.replace("<?xml version=\"1.0\" encoding=\"UTF-8\"?>", "");

    if (!signatureService.isXmlValid(sanitizedRequest)) {
      log.info("Signature is not valid as signature validation fails");
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    RespPayXml respPay = XmlUtil.deserializeXmlDocument(sanitizedRequest, RespPayXml.class);

    retryResponseService.handleRespPay(respPay);
    return new ResponseEntity<>(HttpStatus.ACCEPTED);
  }

  @PostMapping(value = "queryExceptionResponse", produces = "*/*", consumes = TEXT_PLAIN_VALUE)
  @Monitor(name = "DIFF_callback_Response", metricGroup = API_IN)
  @Operation(summary = "Callback api for query exception request", description = "Callback endpoint for exception diff data. This API will receive data requested in ReqQueryExceptionList.")
  @ApiResponse(
    responseCode = "202",
    description = "Accepted",
    content = @Content(
      mediaType = TEXT_PLAIN_VALUE)
  )
  @ApiResponse(
    responseCode = "4xx",
    description = "Failed in api validation",
    content = @Content(
      mediaType = TEXT_PLAIN_VALUE)
  )
  @ApiResponse(
    responseCode = "5xx",
    description = "Service Unavailable Or Internal Server Error",
    content = @Content(
      mediaType = TEXT_PLAIN_VALUE)
  )
  public ResponseEntity<String> respQueryExceptionList(@Parameter(description = "XML request body with response digitally signed by the caller (NETC)") HttpServletRequest req) throws IOException {
    long t1 = System.currentTimeMillis();
    String data = XmlUtil.streamToString(req.getInputStream());
    long t2 = System.currentTimeMillis();
    ResponseEntity<String> responseEntity = retryResponseService.prepareCallbackResponseData(data);
    long t5 = System.currentTimeMillis();
    log.info("DIFF Datalen:{} Times StreamCopy:{} Total:{} ms"
            , req.getContentLength(), t2 - t1, t5 - t1);
    return responseEntity;
  }

  @PostMapping(value = "getExceptionResponse", produces = "*/*", consumes = TEXT_PLAIN_VALUE)
  @ApiOperation("Callback endpoint for exception init data, relates to /reqGetExceptionList api")
  @Monitor(name = "INIT_callback", metricGroup = API_IN)
  @Operation(summary = "Callback api to get data for INIT request", description = "Callback endpoint for exception init data. This API will receive data requested in ReqGetExceptionList.")
  @ApiResponse(
    responseCode = "202",
    description = "Accepted",
    content = @Content(
      mediaType = TEXT_PLAIN_VALUE)
  )
  @ApiResponse(
    responseCode = "5xx",
    description = "Service Unavailable Or Internal Server Error",
    content = @Content(
      mediaType = TEXT_PLAIN_VALUE)
  )
  public ResponseEntity<String> respGetExceptionList(@Parameter(description = "XML request body with response digitally signed by the caller (NETC)") HttpServletRequest req) throws IOException {
    MDC.put("RequestReceiveTime", LocalDateTime.now().toString());
    long t1 = System.currentTimeMillis();
    String data = XmlUtil.streamToString(req.getInputStream());
    long t2 = System.currentTimeMillis();
    long t3 = System.currentTimeMillis();
    RespGetExceptionListXml request = XmlUtil.deserializeXmlDocument(data, RespGetExceptionListXml.class);
    long t4 = System.currentTimeMillis();
    Assert.hasLength(request.getHeader().getMessageId(), "Message Id cannot be empty");
    log.info("Got INIT response for msgId {}, msgNum {}", request.getHeader().getMessageId(),
      request.getTransaction().getResponse().getMessageNumber());
    if (Boolean.TRUE.equals(delegation)) delegationService.getException(data);
    retryResponseService.handleGetExceptionListData(request);
    long t5 = System.currentTimeMillis();
    log.info("INIT Datalen:{} Times StreamCopy:{} SigVerify:{} Deserialize:{} RunLogic:{} Total:{} ms"
      , req.getContentLength(), t2 - t1, t3 - t2, t4 - t3, t5 - t4, t5 - t1);
    MDC.put("RequestReceiveTimeDuration", String.valueOf(t2 - t1));
    MDC.put("BackendProcessTimeDuration", String.valueOf(t5 - t2));
    log.info("{}:{} Header Timestamp: {}, Txn Timestamp: {}, Response Timestamp: {}, Acquirer Processing Time: {} ms",
      request.getHeader().getMessageId(), request.getTransaction().getResponse().getMessageNumber(),
      request.getHeader().getTimeStamp(), request.getTransaction().getTimeStamp(),
      request.getTransaction().getResponse().getTimeStamp(), String.valueOf(t5 - t2));

    MDC.clear();
    return new ResponseEntity<>(HttpStatus.ACCEPTED);
  }

  @PostMapping(value = "heartBeat", produces = APPLICATION_XML_VALUE, consumes = TEXT_PLAIN_VALUE)
  @Monitor(name = "heartbeat", metricGroup = API_IN)
  @Operation(summary = "Check Availability", description = "Used to give the availability of acquirer service.")
  @ApiResponse(
    responseCode = "202",
    description = "Accepted",
    content = @Content(
      mediaType = TEXT_PLAIN_VALUE)
  )
  public ResponseEntity<String> heartbeat() {
    log.info("heartbeat request came in system");
    if (Boolean.TRUE.equals(delegation)) delegationService.heartBeat();
    return new ResponseEntity<>(HttpStatus.ACCEPTED);
  }
}
