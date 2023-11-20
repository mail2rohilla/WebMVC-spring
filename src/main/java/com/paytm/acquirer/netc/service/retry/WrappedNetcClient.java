package com.paytm.acquirer.netc.service.retry;

import com.paytm.acquirer.netc.service.common.NetcClient;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.vavr.control.Try;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@RestController
@AllArgsConstructor
public class WrappedNetcClient {
  private static final Logger log = LoggerFactory.getLogger(WrappedNetcClient.class);

  private final CircuitBreaker circuitBreaker;
  private final NetcClient netcClient;

  public int reqPay(String xml) {
    AtomicInteger code = new AtomicInteger(-1);

    log.info("<--- hitting requestPay with data : {}", xml);

    Supplier<ResponseEntity<Void>> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, () -> netcClient.requestPay(xml));

    ResponseEntity<Void> templateResponse = Try.ofSupplier(decoratedSupplier).recover(throwable -> {
      if (throwable instanceof HttpStatusCodeException) {
        HttpStatusCodeException exception = (HttpStatusCodeException) throwable;
        code.set(exception.getStatusCode().value());
      }
      log.info("respPay failed :{}", throwable.getLocalizedMessage());
      return null;
    }).get();

    if (templateResponse != null) {
      code.set(templateResponse.getStatusCode().value());
    }

    return code.get();
  }

  //this is basically used to trigger HALF OPEN state
  public String requestSyncTime(String xml) {
    Supplier<String> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, () -> netcClient.requestSyncTime(xml));

    return Try.ofSupplier(decoratedSupplier).recover(throwable -> {
      log.info("syncTime failed :{}", throwable.getLocalizedMessage());
      return null;
    }).get();
  }

  //this is basically used to trigger HALF OPEN state
  public String requestDetails(String xml) {
    Supplier<String> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, () -> netcClient.requestDetails(xml));

    return Try.ofSupplier(decoratedSupplier).recover(throwable -> {
      log.info("requestDetails failed :{}", throwable.getLocalizedMessage());
      return null;
    }).get();
  }
  
  //this is basically used to trigger HALF OPEN state
  public String requestListParticipant(String xml) {
    Supplier<String> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, () -> netcClient.requestListParticipant(xml));

    return Try.ofSupplier(decoratedSupplier).recover(throwable -> {
      log.info("requestListParticipant failed :{}", throwable.getLocalizedMessage());
      return null;
    }).get();
  }
}
