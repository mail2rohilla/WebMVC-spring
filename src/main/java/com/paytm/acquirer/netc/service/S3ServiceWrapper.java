package com.paytm.acquirer.netc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytm.transport.metrics.Monitor;
import com.paytm.transport.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class S3ServiceWrapper {
  private final StorageService storageService;
  private final ObjectMapper objectMapper;

  @Value("${storage.file.path}")
  private String STORAGE_DIR;

  @Monitor(name = "createDiffFile", metricGroup = Monitor.ServiceGroup.S3)
  public <T> void createFile(String fileName, List<T> list) throws IOException {
    storageService.saveFileOnStorage(
        new ByteArrayInputStream(objectMapper.writeValueAsString(list).getBytes()),
        fileName,
        STORAGE_DIR);
  }

}
