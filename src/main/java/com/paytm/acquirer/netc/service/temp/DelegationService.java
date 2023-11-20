package com.paytm.acquirer.netc.service.temp;

import com.paytm.acquirer.netc.service.common.RestTemplateService;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

@Service
@EnableAsync
@RequiredArgsConstructor
public class DelegationService {
  private static final Logger log = LoggerFactory.getLogger(DelegationService.class);
  private final RestTemplateService restTemplateService;
  private String base = "http://localhost:8084";

  @Async
  public void reqPay(String data) {
    log.info("delegating call for reqPqy to 8084");
    try {
      restTemplateService.executePostRequest(base + "/responsePayService", Void.class, data);
    } catch (Exception e) {
      log.error("Error occurred in reqPay", e);
    }
  }

  @Async
  public void queryException(String data) {
    log.info("delegating call for queryException to 8084");
    try {
      restTemplateService.executePostRequest(base + "/queryExceptionResponse", Void.class, data);
    } catch (Exception e) {
      log.error("Error occurred in queryException", e);
    }
  }

  @Async
  public void getException(String data) {
    log.info("delegating call for getException to 8084");
    try {
      restTemplateService.executePostRequest(base + "/getExceptionResponse", Void.class, data);
    } catch (Exception e) {
      log.error("Error occurred in getException", e);
    }
  }

  @Async
  public void heartBeat() {
    log.info("delegating call for heartBeat to 8084");
    try {
      restTemplateService.executePostRequest(base + "/heartBeat", Void.class, null);
    } catch (Exception e) {
      log.error("Error occurred in heartbeat", e);
    }
  }
}
