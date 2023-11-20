package com.paytm.acquirer.netc.service;

import com.paytm.acquirer.netc.dto.getException.PassManagerEndpoint;
import com.paytm.acquirer.netc.dto.kafka.ExceptionMetaInfo;
import com.paytm.acquirer.netc.service.common.RestTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PassService {

  private final PassManagerEndpoint passManagerEndpoint;
  private final RestTemplateService restTemplateService;

  @Async("exception-list-executor")
  public void saveActivePassForInit(ExceptionMetaInfo additionalParams) {

    ExceptionMetaInfo requestBody = new ExceptionMetaInfo();
    if(Objects.nonNull(additionalParams)) {
      requestBody.setPlazaIds(additionalParams.getPlazaIds());
    }

    Map<String, String> httpHeaders = new HashMap<>();
    httpHeaders.put("Content-Type", "application/json");

    restTemplateService.executePostRequest(
      passManagerEndpoint.getBaseUrl() + passManagerEndpoint.getSaveActivePassForInit(), Void.class,
      requestBody, httpHeaders, null);
  }
}
