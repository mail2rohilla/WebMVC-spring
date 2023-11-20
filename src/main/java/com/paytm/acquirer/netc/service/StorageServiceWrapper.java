package com.paytm.acquirer.netc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Files;
import com.paytm.transport.metrics.Monitor;
import com.paytm.transport.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

@Service
@RequiredArgsConstructor
@Retryable(maxAttemptsExpression = "#{${s3.retry.max-attempts}}",
    backoff = @Backoff(delayExpression = "#{${s3.retry.delay-ms}}"))
public class StorageServiceWrapper {
  private final StorageService storageService;
  private final ObjectMapper objectMapper;

  @Value("${storage.file.path}")
  private String STORAGE_DIR;

  @Value("${storage.init-file.path}")
  private String INIT_FILE_STORAGE_DIR;


  @Monitor(name = "saveFile", metricGroup = Monitor.ServiceGroup.S3)
  public File saveFile(File zipfile, String fileName) throws IOException {
    byte[] fileByte = Files.toByteArray(zipfile);
    InputStream inputStream = new ByteArrayInputStream(fileByte);
    return storageService.saveFile(
      inputStream,
      fileName,
      INIT_FILE_STORAGE_DIR);
  }
}
