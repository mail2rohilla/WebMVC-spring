package com.paytm.acquirer.netc.service.common;

import com.paytm.acquirer.netc.dto.efkon.TagUpdateResponse;
import com.paytm.acquirer.netc.enums.ExceptionHandlerEndpoint;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.paytm.acquirer.netc.enums.NetcEndpoint.*;

@Component
public class NetcClient {
  private static final Logger log = LoggerFactory.getLogger(NetcClient.class);

  private final String baseUrl;
  private final RestTemplateService restTemplateService;
  private Map<String, String> headers = new HashMap<>();
  Map<String, String> myHeaders = new HashMap<>(headers);
  private static final String CONTENT_TYPE= "Content-Type";
  @Value("${exception-handler-base-url}")
  private String exceptionHandleBaseUrl;

  public NetcClient(@Value("${netc.switch.base-url}") String baseUrl, RestTemplateService restTemplateService) {
    this.baseUrl = baseUrl;
    this.restTemplateService = restTemplateService;
    headers.put("accept", "*/*");
    myHeaders.put(CONTENT_TYPE, "text/xml");
  }

  @Retryable(
      value = {ResourceAccessException.class},
      maxAttemptsExpression = "#{${request.retry.max-attempts}}",
      backoff = @Backoff(
          delayExpression = "#{${request.retry.delay-ms}}",
          multiplierExpression = "#{${request.retry.multiplier}}",
          maxDelayExpression = "#{${request.retry.max-delay-ms}}"))
  public String requestDetails(String body) {
    log.info("hitting netc service for request Details");
    return restTemplateService.executePostRequest(REQ_DETAILS.getEndpointUrl(baseUrl), String.class, body, myHeaders, null);
  }
  
  @Retryable(
    value = {ResourceAccessException.class},
    maxAttemptsExpression = "#{${request.retry.max-attempts}}",
    backoff = @Backoff(
      delayExpression = "#{${request.retry.delay-ms}}",
      multiplierExpression = "#{${request.retry.multiplier}}",
      maxDelayExpression = "#{${request.retry.max-delay-ms}}"))
  public String requestListParticipant(String body) {
    log.info("hitting netc service for requestListParticipant");
    return restTemplateService.executePostRequest(LIST_PARTICIPANT.getEndpointUrl(baseUrl), String.class, body, myHeaders, null);
  }
  
  @Retryable(
      value = {ResourceAccessException.class},
      maxAttemptsExpression = "#{${request.retry.max-attempts}}",
      backoff = @Backoff(
          delayExpression = "#{${request.retry.delay-ms}}",
          multiplierExpression = "#{${request.retry.multiplier}}",
          maxDelayExpression = "#{${request.retry.max-delay-ms}}"))
  public ResponseEntity<Void> requestPay(String body) {
    log.info("hitting netc service for reqPay");
    return restTemplateService.executePostRequestReturnResponseEntity(REQ_PAY.getEndpointUrl(baseUrl), Void.class, body, headers, null);
  }

  @Retryable(
      value = {ResourceAccessException.class},
      maxAttemptsExpression = "#{${request.retry.max-attempts}}",
      backoff = @Backoff(
          delayExpression = "#{${request.retry.delay-ms}}",
          multiplierExpression = "#{${request.retry.multiplier}}",
          maxDelayExpression = "#{${request.retry.max-delay-ms}}"))
  public ResponseEntity<Void> requestGetExceptionList(String body) {
    log.info("hitting request query exception");
    return restTemplateService.executePostRequestReturnResponseEntity(GET_EXCEPTION_LIST.getEndpointUrl(baseUrl), Void.class, body, headers, null);
  }

  @Retryable(
      value = {ResourceAccessException.class},
      maxAttemptsExpression = "#{${request.retry.max-attempts}}",
      backoff = @Backoff(
          delayExpression = "#{${request.retry.delay-ms}}",
          multiplierExpression = "#{${request.retry.multiplier}}",
          maxDelayExpression = "#{${request.retry.max-delay-ms}}"))
  public ResponseEntity<Void> requestQueryExceptionList(String body) {
    log.info("hitting request query exception");
    return restTemplateService.executePostRequestReturnResponseEntity(QUERY_EXCEPTION_LIST.getEndpointUrl(baseUrl), Void.class, body, headers, null);
  }

  @Retryable(
    value = {ResourceAccessException.class},
    maxAttemptsExpression = "#{${request.retry.max-attempts}}",
    backoff = @Backoff(
      delayExpression = "#{${request.retry.delay-ms}}",
      multiplierExpression = "#{${request.retry.multiplier}}",
      maxDelayExpression = "#{${request.retry.max-delay-ms}}"))
  public ResponseEntity<String> requestTransactionCheck(String body) {
    log.info("hitting netc service for check Txn Status");
    return restTemplateService.executePostRequestReturnResponseEntity(REQ_TXN_STATUS.getEndpointUrl(baseUrl), String.class, body,headers,null);
  }

  @Retryable(
      value = {ResourceAccessException.class},
      maxAttemptsExpression = "#{${request.retry.max-attempts}}",
      backoff = @Backoff(
          delayExpression = "#{${request.retry.delay-ms}}",
          multiplierExpression = "#{${request.retry.multiplier}}",
          maxDelayExpression = "#{${request.retry.max-delay-ms}}"))
  public String requestManageException(String body) {
    log.info("hitting request manage exception");
    return restTemplateService.executePostRequest(MNG_EXCEPTION.getEndpointUrl(baseUrl), String.class, body, myHeaders, null);
  }

  public String requestSyncTime(String body) {
    return restTemplateService.executePostRequest(SYNC_TIME.getEndpointUrl(baseUrl), String.class, body, myHeaders, null);
  }


  public void sendTagDetailsToExceptionHandler(List<TagUpdateResponse> tagUpdateResponses) {
    final Map<String, String> httpHeaders = new HashMap<>();
    httpHeaders.put(CONTENT_TYPE, "application/json");
    String url = exceptionHandleBaseUrl + "/" + "test/api/exc-handler/insertEfkonTags";
    restTemplateService.executePostRequest(url, void.class, tagUpdateResponses, httpHeaders, null);
  }

  public Void requestExceptionHandlerToPreloadTagsData(String messageId) {
    final Map<String, String> httpHeaders = new HashMap<>();
    httpHeaders.put(CONTENT_TYPE, "application/json");
    String url = ExceptionHandlerEndpoint.PRE_LOAD_TAGS_DATA.getEndpointUrl(exceptionHandleBaseUrl) ;
    String preloadDataUrl = UriComponentsBuilder.fromHttpUrl(url).queryParam("messageId", "{messageId}").encode().toUriString();

    log.info("requesting exception handler to pre-load tags data from database");
    restTemplateService.executeGetRequest(preloadDataUrl, String.class, httpHeaders, CollectionUtils.toMultiValueMap(Collections.singletonMap("messageId", Collections.singletonList(messageId))));
    return null;
  }
}
