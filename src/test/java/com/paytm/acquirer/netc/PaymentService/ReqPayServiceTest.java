package com.paytm.acquirer.netc.PaymentService;

import com.paytm.acquirer.netc.config.properties.RetryProperties;
import com.paytm.acquirer.netc.config.properties.TimeoutProperties;
import com.paytm.acquirer.netc.db.entities.AsyncTransaction;
import com.paytm.acquirer.netc.db.repositories.master.AsyncTransactionMasterRepository;
import com.paytm.acquirer.netc.db.repositories.master.PlazaTxnCounterMasterRepository;
import com.paytm.acquirer.netc.dto.common.VehicleDetails;
import com.paytm.acquirer.netc.dto.pay.ReqPay;
import com.paytm.acquirer.netc.dto.pay.RespPay;
import com.paytm.acquirer.netc.enums.NetcEndpoint;
import com.paytm.acquirer.netc.enums.PlazaCategory;
import com.paytm.acquirer.netc.enums.PlazaType;
import com.paytm.acquirer.netc.enums.Status;
import com.paytm.acquirer.netc.enums.TransactionType;
import com.paytm.acquirer.netc.kafka.producer.KafkaProducer;
import com.paytm.acquirer.netc.service.AsyncTxnService;
import com.paytm.acquirer.netc.service.ReminderService;
import com.paytm.acquirer.netc.service.ReqPayService;
import com.paytm.acquirer.netc.service.ReqPayTollService;
import com.paytm.acquirer.netc.service.common.MetadataService;
import com.paytm.acquirer.netc.service.common.NetcClient;
import com.paytm.acquirer.netc.service.common.RedisService;
import com.paytm.acquirer.netc.service.common.RestTemplateService;
import com.paytm.acquirer.netc.service.common.SignatureService;
import com.paytm.acquirer.netc.service.retry.WrappedNetcClient;
import com.paytm.acquirer.netc.util.FactoryMethodService;
import com.paytm.acquirer.netc.util.Utils;
import com.paytm.transport.service.AcquirerReminderService;
import com.paytm.transport.service.TollIdMappingService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ReqPayServiceTest {

  @Mock
  AsyncTransactionMasterRepository asyncTransactionMasterRepository;
  @Mock
  PlazaTxnCounterMasterRepository plazaTxnCounterMasterRepository;
  @Mock
  ApplicationContext context;
  @Mock
  TollIdMappingService tollIdMappingService;
  @Mock
  SignatureService signatureService;
  @Mock
  CircuitBreaker circuitBreaker;
  @Mock
  RestTemplateService restTemplateService;
  @Mock
  KafkaProducer kafkaProducer;
  @Mock
  AcquirerReminderService acquirerReminderService;
  @Mock
  ValueOperations<String, Object> valueOperations;
  @Mock
  RedisTemplate<String, Object> redisTemplate;

  @Spy
  @InjectMocks
  ReminderService reminderService;
  @InjectMocks
  NetcClient netcClient;
  @InjectMocks
  RedisService redisService;
  @InjectMocks
  TimeoutProperties timeoutProperties;
  @InjectMocks
  RetryProperties retryProperties;
  @InjectMocks
  MetadataService metadataService;
  @InjectMocks
  FactoryMethodService factoryMethodService;
  @InjectMocks
  AsyncTxnService asyncTxnService;
  @InjectMocks
  WrappedNetcClient retryWrapperService;
  @InjectMocks
  ReqPayService reqPayService;

  @Captor
  ArgumentCaptor<RespPay> respPayCaptor;

  private static final String dummyTagId = "34161FA820328E400D4464E0";
  private static final String dummyMsgId = "MSG00521627885793441198153M441";

  @Before
  public void setUp() throws Exception {
    ReflectionTestUtils.setField(reqPayService, "asyncTxnService", asyncTxnService);
    ReflectionTestUtils.setField(reqPayService, "retryProperties", retryProperties);
    ReflectionTestUtils.setField(reqPayService, "metadataService", metadataService);
    ReflectionTestUtils.setField(reqPayService, "timeoutProperties", timeoutProperties);
    ReflectionTestUtils.setField(reqPayService, "factoryMethodService", factoryMethodService);
    ReflectionTestUtils.setField(reqPayService, "retryWrapperService", retryWrapperService);
    ReflectionTestUtils.setField(reqPayService, "reminderService", reminderService);
    ReflectionTestUtils.setField(reqPayService, "tasNpciTimeDiff", 1L);
    ReflectionTestUtils.setField(reqPayService, "errorCodes", "-1");
    ReflectionTestUtils.setField(reqPayService, "parsedErrorCodes", Collections.singletonList(-1));
    ReflectionTestUtils.setField(metadataService, "redisService", redisService);
    ReflectionTestUtils.setField(metadataService, "orgId", "TEST-ORG");
    ReflectionTestUtils.setField(retryWrapperService, "netcClient", netcClient);

    when(tollIdMappingService.getNewMapping(anyString())).thenReturn("123456");
    when(context.getBean(ReqPayTollService.class)).thenReturn(new ReqPayTollService());
    when(signatureService.signXmlDocument(anyString())).thenReturn("SIGNED STRING");
    when(restTemplateService.executePostRequestReturnResponseEntity(anyString(), eq(Void.class), anyString(),
      any(), eq(null))).thenReturn(getResponseEntity());
    when(asyncTransactionMasterRepository.findByMsgIdAndApi(anyString(), any()))
      .thenReturn(Optional.of(new AsyncTransaction()));
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

  }

  @Test
  public void requestPaymentForNewTransactionTest() {
    reqPayService.requestPayment(getReqPay());

    verify(asyncTransactionMasterRepository, times(2)).save(any());
    verify(kafkaProducer, times(1)).send(respPayCaptor.capture());
    Assert.assertEquals(Status.REQUEST_OK.getTxnManagerStatus(), respPayCaptor.getValue().getStatus());
  }

  @Test
  public void requestPaymentWhenBankIdMissingTest() {
    ReqPay reqPay = getReqPay();
    reqPay.setBankId(null);
    reqPayService.requestPayment(reqPay);

    verify(asyncTransactionMasterRepository, times(2)).save(any());
    verify(kafkaProducer, times(1)).send(respPayCaptor.capture());
    Assert.assertEquals(Status.REQUEST_OK.getTxnManagerStatus(), respPayCaptor.getValue().getStatus());
  }

  @Test
  public void requestPaymentWhenVehicleDetailsMissingTest() {
    ReqPay reqPay = getReqPay();
    reqPay.setBankId(null);
    reqPay.setVehicleDetails(null);
    reqPayService.requestPayment(reqPay);

    verify(asyncTransactionMasterRepository, times(2)).save(any());
    verify(kafkaProducer, times(1)).send(respPayCaptor.capture());
    Assert.assertEquals(Status.REQUEST_OK.getTxnManagerStatus(), respPayCaptor.getValue().getStatus());
  }

  @Test
  public void requestPaymentOnRetryTest() {

    when(asyncTransactionMasterRepository.findFirstByApiAndRefIdOrderByIdDesc(eq(NetcEndpoint.REQ_PAY), anyString()))
      .thenReturn(Optional.of(getAsyncTransaction()));

    reqPayService.requestPayment(getReqPay());

    verify(asyncTransactionMasterRepository, times(2)).save(any());
    verify(kafkaProducer, times(1)).send(respPayCaptor.capture());
    Assert.assertEquals(Status.REQUEST_OK.getTxnManagerStatus(), respPayCaptor.getValue().getStatus());
  }

  @Test
  public void requestPaymentOnRetryExceededTest() {
    AsyncTransaction asyncTransaction = getAsyncTransaction();
    asyncTransaction.setMsgId("MSG03521627885793441198153M441");
    when(asyncTransactionMasterRepository.findFirstByApiAndRefIdOrderByIdDesc(eq(NetcEndpoint.REQ_PAY), anyString()))
      .thenReturn(Optional.of(asyncTransaction));

    reqPayService.requestPayment(getReqPay());

    verify(asyncTransactionMasterRepository, times(0)).save(any());
    verify(kafkaProducer, times(0)).send(respPayCaptor.capture());
  }

  @Test
  public void requestPaymentOnRequestFailed5xxTest() {
    ResponseEntity.BodyBuilder responseEntity = ResponseEntity.status(503);

    when(restTemplateService.executePostRequestReturnResponseEntity(anyString(), eq(Void.class), anyString(),
      any(), eq(null))).thenReturn(responseEntity.build());

    reqPayService.requestPayment(getReqPay());

    verify(asyncTransactionMasterRepository, times(2)).save(any());
    verify(kafkaProducer, times(1)).send(respPayCaptor.capture());
    verify(reminderService, times(1)).addReminder(any(), eq(null), anyLong());
    Assert.assertEquals(Status.REQUEST_FAILED.getTxnManagerStatus(), respPayCaptor.getValue().getStatus());
  }

  @Test
  public void requestPaymentOnRequestFailed4xxTest() {
    ResponseEntity.BodyBuilder responseEntity = ResponseEntity.status(404);

    when(restTemplateService.executePostRequestReturnResponseEntity(anyString(), eq(Void.class), anyString(),
      any(), eq(null))).thenReturn(responseEntity.build());

    reqPayService.requestPayment(getReqPay());

    verify(asyncTransactionMasterRepository, times(2)).save(any());
    verify(kafkaProducer, times(1)).send(respPayCaptor.capture());
    verify(reminderService, times(0)).addReminder(any(), eq(null), anyLong());
    Assert.assertEquals(Status.REQUEST_FAILED.getTxnManagerStatus(), respPayCaptor.getValue().getStatus());
  }

  @Test
  public void asyncRequestPaymentForNewTransactionTest() {
    reqPayService.asyncRequestPayment(getReqPay());

    verify(asyncTransactionMasterRepository, times(2)).save(any());
    verify(kafkaProducer, times(1)).send(respPayCaptor.capture());
    Assert.assertEquals(Status.REQUEST_OK.getTxnManagerStatus(), respPayCaptor.getValue().getStatus());
  }

  @Test
  public void requestPaymentExceptionThrownTest() {
    when(asyncTransactionMasterRepository.save(any())).thenThrow(DataIntegrityViolationException.class);

    reqPayService.requestPayment(getReqPay());

    verify(asyncTransactionMasterRepository, times(1)).save(any());
    verify(kafkaProducer, times(0)).send(respPayCaptor.capture());
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

  private ResponseEntity<Void> getResponseEntity() {
    ResponseEntity.BodyBuilder responseEntity = ResponseEntity.status(202);
    return responseEntity.build();
  }

  private AsyncTransaction getAsyncTransaction() {
    AsyncTransaction asyncTransaction = new AsyncTransaction();
    asyncTransaction.setId(123L);
    asyncTransaction.setTxnId("TX123456");
    asyncTransaction.setMsgId(dummyMsgId);
    return asyncTransaction;
  }
}
