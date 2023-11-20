package com.paytm.acquirer.netc.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;

@RestControllerAdvice
public class ControllerExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ControllerExceptionHandler.class);

  @Value("#{environment.acceptsProfiles('staging', 'prod') ? 'false' : 'true' }")
  private Boolean showDetails;

  @ExceptionHandler
  public final ResponseEntity<ApiError> handleException(RuntimeException ex) {
    String message = ex.getMessage();
    Throwable cause = Boolean.TRUE.equals(showDetails) ? ex.getCause() : null;
    log.error(ex.getMessage(), ex);

    // thrown when spring cannot parse message body
    if (ex instanceof HttpMessageNotReadableException
        || ex instanceof IllegalArgumentException) {
      return ResponseEntity.badRequest().body(new ApiError(message, cause));
    } else if (ex instanceof HttpClientErrorException) {
      message = String.format("Got '%s' error while communicating with NETC.", ex.getMessage());
    } else if (ex instanceof NetcEngineException) {
      Integer status = ((NetcEngineException) ex).getResponseCode();
      if (status != null) {
        return ResponseEntity.status(status).body(new ApiError(message, cause));
      }
    }

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(message, cause));
  }


  @AllArgsConstructor
  @Data
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private static class ApiError {
    private String message;
    private Object cause;
  }

}
