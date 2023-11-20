package com.paytm.acquirer.netc.PaymentService;

import com.paytm.acquirer.netc.adapter.KafkaAdapter;
import com.paytm.acquirer.netc.dto.common.VehicleDetails;
import com.paytm.acquirer.netc.dto.pay.ReqPay;
import com.paytm.acquirer.netc.dto.pay.ReqPayXml;
import com.paytm.acquirer.netc.dto.retry.RetryDto;
import com.paytm.acquirer.netc.enums.PlazaCategory;
import com.paytm.acquirer.netc.enums.PlazaType;
import com.paytm.acquirer.netc.enums.RetrialType;
import com.paytm.acquirer.netc.enums.TransactionType;
import com.paytm.acquirer.netc.kafka.producer.KafkaProducer;
import com.paytm.acquirer.netc.service.ReqPayService;
import com.paytm.acquirer.netc.service.ReqPayTollService;
import com.paytm.acquirer.netc.service.common.MetadataService;
import com.paytm.acquirer.netc.service.common.RedisService;
import com.paytm.acquirer.netc.util.Utils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ReqPayRetryTest {

  @Mock
  KafkaProducer kafkaProducer;
  @Mock
  ValueOperations<String, Object> valueOperations;
  @Mock
  RedisTemplate<String, Object> redisTemplate;

  @InjectMocks
  RedisService redisService;
  @InjectMocks
  MetadataService metadataService;
  @InjectMocks
  ReqPayService reqPayService;

  private static final String dummyTagId = "34161FA820328E400D4464E0";

  @Before
  public void setUp() throws Exception {
    ReflectionTestUtils.setField(metadataService, "redisService", redisService);
    ReflectionTestUtils.setField(metadataService, "orgId", "TEST-ORG");

    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
  }

  @Test
  public void requestPaymentRetryOnFailure() {
    RetryDto retryDto = getRetryDto(RetrialType.CIRCUIT_BREAKER_RETRY);
    reqPayService.handleCircuitBreakerReqPay(retryDto);

    verify(kafkaProducer, times(1)).sendReqPayRetryMsg(any());
  }
  
  @Test
  public void autoRetryReqPayTest() {
    RetryDto retryDto = getRetryDto(RetrialType.AUTO_RETRYABLE_CODE_RETRY);
    reqPayService.handleAutoRetryableCode(retryDto);
    
    verify(kafkaProducer, times(1)).notifyTxnManagerToRetryReqPay(any());
  }
  
  private RetryDto getRetryDto(RetrialType isCircuitBreakerRetry) {
    ReqPayTollService iReqPayService = new ReqPayTollService();
    ReqPay reqPay = getReqPay();
    String requestTime = Utils.getFormattedDate(LocalDateTime.now().minusMinutes(1));
    ReqPayXml request = iReqPayService.convertReqPayToXml(reqPay, requestTime, metadataService);
    request.setHeader(metadataService.createXmlHeader(request.getTransaction().getId(), requestTime, null, 0));
    return KafkaAdapter.createKafkaMsgForReqPay(request, reqPay, isCircuitBreakerRetry);
  }

  private ReqPay getReqPay() {
    ReqPay reqPay = new ReqPay();
    reqPay.setTxnReferenceId("123456");
    reqPay.setTxnTime(Utils.getFormattedDate(LocalDateTime.now().minusMinutes(1)));
    reqPay.setTxnType(TransactionType.DEBIT);
    reqPay.setPlazaId("123");
    reqPay.setPlazaName("Test Plaza");
    reqPay.setPlazaGeoCode("1,2");
    reqPay.setPlazaType(PlazaType.NATIONAL);
    reqPay.setLaneId("2");
    reqPay.setLaneDirection("S");
    reqPay.setTagReadTime(Utils.getFormattedDate(LocalDateTime.now().minusMinutes(1)));
    reqPay.setTagId(dummyTagId);
    reqPay.setBankId("1234");
    reqPay.setAvc("VC10");
    reqPay.setAmount("100.0");
    reqPay.setAcquirerId("1235");
    reqPay.setVehicleDetails(getVehicleDetails());
    reqPay.setPlazaTxnId("TXN123456789");
    reqPay.setPlazaCategory(PlazaCategory.TOLL);
    return reqPay;
  }

  private VehicleDetails getVehicleDetails() {
    VehicleDetails vehicleDetails = new VehicleDetails();
    vehicleDetails.setTagId(dummyTagId);
    vehicleDetails.setRegNumber("IN1234");
    vehicleDetails.setTid("112345678");
    vehicleDetails.setVehicleClass("VC10");
    vehicleDetails.setTagStatus("A");
    vehicleDetails.setIssueDate(LocalDate.now().minusMonths(1).toString());
    vehicleDetails.setCommercialVehicle("F");
    vehicleDetails.setBankId("1234");
    vehicleDetails.setExceptionCodes(Collections.singletonList("00"));
    return vehicleDetails;
  }
}
