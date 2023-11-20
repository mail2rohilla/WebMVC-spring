package com.paytm.acquirer.netc.controller.temp;

import com.paytm.acquirer.netc.service.temp.MockService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile({"dev", "uaws"})
@RestController
@RequiredArgsConstructor
@Hidden
public class MockController {

  private final MockService mockService;

  @PostMapping("syncTimeRequest")
  public String syncTimeRequestMock() {
    return mockService.syncTimeRequestMock();
  }

  @PostMapping("requestDetail")
  public String requestDetailMock() {
    return mockService.requestDetailMock();
  }

  @PostMapping("reqPayService")
  public ResponseEntity<Void> reqPayServiceMock() {
    return mockService.reqAcceptedMock();
  }

  @PostMapping("getExceptionList")
  public ResponseEntity<Void> getExceptionListMock() {
    return mockService.reqAcceptedMock();
  }

  @PostMapping("queryException")
  public ResponseEntity<Void> queryExceptionMock() {
    return mockService.reqAcceptedMock();
  }

  @PostMapping("checkTxnStatus")
  public String checkTxnStatusMock() {
    return mockService.checkTxnStatusMock();
  }

  @PostMapping("manageException")
  public String manageExceptionMock() {
    return mockService.manageExceptionMock();
  }

}
