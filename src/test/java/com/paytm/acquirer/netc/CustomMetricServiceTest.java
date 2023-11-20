package com.paytm.acquirer.netc;

import com.paytm.acquirer.netc.service.CustomMetricService;
import com.paytm.transport.metrics.DataDogClient;
import com.paytm.transport.metrics.Monitor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class CustomMetricServiceTest {

  @InjectMocks
  private CustomMetricService customMetricService;
  
  @Mock
  private DataDogClient dataDogClient;
  
  @Test
  public void recordMetricForCircuitBreaker() {
    customMetricService.recordMetricForCircuitBreaker(Monitor.ServiceGroup.COUNTER, "HALF_OPEN_TO_OPEN");
    verify(dataDogClient, times(1)).incrementCounter((Monitor.ServiceGroup) any(), (Map<String, String>) any());
  }
}
