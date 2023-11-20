package com.paytm.acquirer.netc.exception;

public class StorageServiceException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public StorageServiceException(String message, Throwable t) {
    super(message, t);
  }

  public StorageServiceException(String message) {
    super(message);
  }

  public StorageServiceException(Throwable t) {
    super(t);
  }
}
