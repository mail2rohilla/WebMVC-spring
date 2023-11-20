package com.paytm.acquirer.netc.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.HttpStatusCodeException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom error handler which logs error info before throwing exception
 */
public class RestServiceErrorHandler extends DefaultResponseErrorHandler {
  private static final Logger log = LoggerFactory.getLogger(RestServiceErrorHandler.class);
  private ObjectMapper mapper = new ObjectMapper();

  @Override
  protected void handleError(ClientHttpResponse response, HttpStatus statusCode) throws IOException {
    try {
      super.handleError(response, statusCode);
    }
    catch (HttpStatusCodeException ex) {
      logError(ex);
      throw ex;
    }
  }

  private void logError(HttpStatusCodeException statusCodeEx) {
    Map<String, String> map = new HashMap<>();
    map.put("StatusCode", statusCodeEx.getStatusCode().toString());
    map.put("Message", statusCodeEx.getMessage());
    map.put("StatusText", statusCodeEx.getStatusText());
    map.put("Body", statusCodeEx.getResponseBodyAsString());
    try {
      log.error("--> error from api : {}", mapper.writeValueAsString(map));
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }

  }
}