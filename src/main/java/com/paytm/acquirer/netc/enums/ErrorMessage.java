package com.paytm.acquirer.netc.enums;

import org.springframework.http.HttpStatus;

public enum ErrorMessage {
  SFTP_SESSION_CREATION_FAILURE(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to creation session"),
  FILE_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "File not found"),
  FILE_VALIDATION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Exception while file validation"),
  INVALID_FILE_HEADERS(HttpStatus.INTERNAL_SERVER_ERROR, "Header validation failure"),
  INVALID_FILE_FORMAT(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid file format"),
  INVALID_GET_EXCEPTION_CALLBACK(HttpStatus.BAD_REQUEST, "Got response callback for unknown msgId %s"),
  INIT_IN_PROGRESS(HttpStatus.BAD_REQUEST, "No new INIT request is allowed while INIT is already in progress"),
  INVALID_OBJECT_CONVERSION(HttpStatus.INTERNAL_SERVER_ERROR, "Error while object conversion"),
  GET_EXCEPTION_CALLBACK_TIMED_OUT(HttpStatus.BAD_REQUEST, "Got response of timed out INIT request with msgId");

  private final HttpStatus httpStatus;
  private final String message;

  ErrorMessage(HttpStatus httpStatus, String message) {
    this.httpStatus = httpStatus;
    this.message = message;
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }

  public String getMessage() {
    return message;
  }
}
