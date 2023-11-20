package com.paytm.acquirer.netc.PaymentService;

import com.paytm.acquirer.netc.TestingConditions;
import com.paytm.acquirer.netc.config.properties.RetryProperties;
import com.paytm.acquirer.netc.config.properties.TimeoutProperties;
import com.paytm.acquirer.netc.db.entities.AsyncTransaction;
import com.paytm.acquirer.netc.db.entities.ErrorCodeMapping;
import com.paytm.acquirer.netc.db.repositories.master.AsyncTransactionMasterRepository;
import com.paytm.acquirer.netc.db.repositories.master.ErrorCodeMappingMasterRepository;
import com.paytm.acquirer.netc.dto.common.HeaderXml;
import com.paytm.acquirer.netc.dto.common.RespPayResponseXml;
import com.paytm.acquirer.netc.dto.common.RiskScoreTxnXml;
import com.paytm.acquirer.netc.dto.pay.RespPayXml;
import com.paytm.acquirer.netc.enums.HandlerType;
import com.paytm.acquirer.netc.enums.NetcEndpoint;
import com.paytm.acquirer.netc.enums.Status;
import com.paytm.acquirer.netc.kafka.producer.KafkaProducer;
import com.paytm.acquirer.netc.service.AsyncTxnService;
import com.paytm.acquirer.netc.service.CustomMetricService;
import com.paytm.acquirer.netc.service.ReminderService;
import com.paytm.acquirer.netc.service.RespPayService;
import com.paytm.acquirer.netc.service.retry.RetryResponseService;
import com.paytm.acquirer.netc.util.Constants;
import com.paytm.acquirer.netc.util.JsonUtil;
import com.paytm.transport.metrics.DataDogClient;
import com.paytm.transport.service.AcquirerReminderService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.paytm.acquirer.netc.CommonTestFunction.DUMMY_MSG_ID_MAX_RETRY;
import static com.paytm.acquirer.netc.util.Constants.TRANSACTION_DECLINED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ResponsePayServiceTest {

  @Mock
  KafkaProducer kafkaProducer;
  @Mock
  AcquirerReminderService acquirerReminderService;
  @Mock
  AsyncTransactionMasterRepository asyncTransactionMasterRepository;
  @Mock
  ErrorCodeMappingMasterRepository errorCodeMappingRepository;
  @Mock
  DataDogClient dataDogClient;
 
  @InjectMocks
  RespPayService respPayService;
  @InjectMocks
  ReminderService reminderService;
  @InjectMocks
  AsyncTxnService asyncTxnService;
  @InjectMocks
  RetryResponseService retryResponseService;
  @InjectMocks
  RetryProperties retryProperties;
  @InjectMocks
  CustomMetricService customMetricService;
  @InjectMocks
  TimeoutProperties timeoutProperties;
  
  private static final String txnId = "TXN123456789";
  private static final String plazaId = "543210";
  private static final String dummyTagId = "34161FA820328E400D4464E0";
  private static final String dummyMsgId = "MSG00521627885793441198153M441";

  @Before
  public void setUp() throws Exception {
    ReflectionTestUtils.setField(retryResponseService, "asyncTxnService", asyncTxnService);
    ReflectionTestUtils.setField(retryResponseService, "reminderService", reminderService);
    ReflectionTestUtils.setField(retryResponseService, "respPayService", respPayService);
    ReflectionTestUtils.setField(retryResponseService, "customMetricService", customMetricService);
    ReflectionTestUtils.setField(retryResponseService, "retryProperties", retryProperties);
    ReflectionTestUtils.setField(retryResponseService, "timeoutProperties", timeoutProperties);
  
    when(asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(anyString(), eq(NetcEndpoint.REQ_PAY), anyInt()))
      .thenReturn(Optional.of(getAsyncTransaction()));
    when(asyncTransactionMasterRepository.findFirstByApiAndRefIdAndCreatedAtLessThanEqualOrderByIdDesc(
      eq(NetcEndpoint.REQ_PAY), anyString(), any())).thenReturn(Optional.of(getAsyncTransaction()));
    when(errorCodeMappingRepository.findByHandlerNotNull()).thenReturn(getErrorCodeList());
  }

  @Test
  public void responsePayTest() {
    TestingConditions testingConditions = new TestingConditions();
    retryResponseService.handleRespPay(getRespPayXml(testingConditions));

    verify(kafkaProducer, times(1)).send(any());
  }

  @Test
  public void responsePayWithMetaDataTest() {
    TestingConditions testingConditions = new TestingConditions();
    AsyncTransaction asyncTransaction = getAsyncTransaction();
    asyncTransaction.setMetaData(JsonUtil.serialiseJson(Collections.singletonMap(Constants.PLAZA_ID, plazaId)));

    when(asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(anyString(), eq(NetcEndpoint.REQ_PAY), anyInt()))
      .thenReturn(Optional.of(asyncTransaction));

    retryResponseService.handleRespPay(getRespPayXml(testingConditions));

    verify(kafkaProducer, times(1)).send(any());
  }
  
  @Test
  public void responsePay201Test() {
    TestingConditions testingConditions = new TestingConditions();
    testingConditions.setResponseCode("201");
    retryResponseService.handleRespPay(getRespPayXml(testingConditions));
    
    verify(kafkaProducer, times(1)).send(any());
  }
  
  @Test
  public void responsePayAutoRetryTest() {
    TestingConditions testingConditions = new TestingConditions();
    testingConditions.setTxnStatus(TRANSACTION_DECLINED);
    testingConditions.setResponseCode("123");
    when(asyncTransactionMasterRepository.findByMsgIdAndApi(anyString(), any())).thenReturn(Optional.of(new AsyncTransaction()));
    retryResponseService.handleRespPay(getRespPayXml(testingConditions));
    
    verify(kafkaProducer, times(1)).send(any());
  }
  
  @Test
  public void responsePayAutoRetryMaxLimitTest() {
    TestingConditions testingConditions = new TestingConditions();
    testingConditions.setTxnStatus(TRANSACTION_DECLINED);
    testingConditions.setResponseCode("123");
    testingConditions.setMaxRetryLimit(true);
    when(asyncTransactionMasterRepository.findByMsgIdAndApi(anyString(), any())).thenReturn(Optional.of(new AsyncTransaction()));
    retryResponseService.handleRespPay(getRespPayXml(testingConditions));
    
    verify(kafkaProducer, times(1)).send(any());
    verify(kafkaProducer, times(0)).notifyTxnManagerToRetryReqPay(any());
  }
  
  @Test
  public void responsePayManualRetryTest() {
    TestingConditions testingConditions = new TestingConditions();
    testingConditions.setTxnStatus(TRANSACTION_DECLINED);
    testingConditions.setResponseCode("124");
    when(asyncTransactionMasterRepository.findByMsgIdAndApi(anyString(), any())).thenReturn(Optional.of(new AsyncTransaction()));
    retryResponseService.handleRespPay(getRespPayXml(testingConditions));
    
    verify(kafkaProducer, times(1)).send(any());
    verify(kafkaProducer, times(0)).notifyTxnManagerToRetryReqPay(any());
  }
  
  @Test
  public void responsePayAutoBlackListTest() {
    TestingConditions testingConditions = new TestingConditions();
    testingConditions.setTxnStatus(TRANSACTION_DECLINED);
    testingConditions.setResponseCode("125");
    retryResponseService.handleRespPay(getRespPayXml(testingConditions));
    
    verify(kafkaProducer, times(1)).send(any());
    verify(kafkaProducer, times(0)).notifyTxnManagerToRetryReqPay(any());
  }
  
  @Test
  public void responsePayNonRetryableErrorTest() {
    TestingConditions testingConditions = new TestingConditions();
    testingConditions.setTxnStatus(TRANSACTION_DECLINED);
    testingConditions.setResponseCode("126");
    retryResponseService.handleRespPay(getRespPayXml(testingConditions));
    
    verify(kafkaProducer, times(1)).send(any());
    verify(kafkaProducer, times(0)).notifyTxnManagerToRetryReqPay(any());
  }
  
  private RespPayXml getRespPayXml(TestingConditions testingConditions) {
    RespPayXml respPayXml = new RespPayXml();
    RiskScoreTxnXml riskScoreTxnXml = new RiskScoreTxnXml();
    riskScoreTxnXml.setId(txnId);
    RespPayResponseXml respPayResponseXml = new RespPayResponseXml();
    respPayResponseXml.setResponseCode(testingConditions.getResponseCode());
    respPayResponseXml.setResult(testingConditions.getTxnStatus());
    HeaderXml headerXml = new HeaderXml();
    headerXml.setMessageId(dummyMsgId);
    if(testingConditions.isMaxRetryLimit()) {
      headerXml.setMessageId(DUMMY_MSG_ID_MAX_RETRY);
    }
    respPayXml.setRiskScoreTxn(riskScoreTxnXml);
    respPayXml.setResponse(respPayResponseXml);
    respPayXml.setHeader(headerXml);
    return respPayXml;
  }

  private AsyncTransaction getAsyncTransaction() {
    AsyncTransaction asyncTransaction = new AsyncTransaction();
    asyncTransaction.setRefId("REF12345");
    asyncTransaction.setStatus(Status.REQUEST_OK);
    asyncTransaction.setCreatedAt(Timestamp.valueOf(LocalDateTime.now().minusSeconds(10)));
    return asyncTransaction;
  }
  
  private List<ErrorCodeMapping> getErrorCodeList() {
    ErrorCodeMapping errorCodeMapping = new ErrorCodeMapping();
    errorCodeMapping.setErrorCode("123");
    errorCodeMapping.setHandler(HandlerType.AUTO_RETRY);
    
    ErrorCodeMapping errorCodeMapping1 = new ErrorCodeMapping();
    errorCodeMapping1.setErrorCode("124");
    errorCodeMapping1.setHandler(HandlerType.MANUAL_RETRY);
    
    ErrorCodeMapping errorCodeMapping2 = new ErrorCodeMapping();
    errorCodeMapping2.setErrorCode("125");
    errorCodeMapping2.setHandler(HandlerType.AUTO_BLACK_LIST);
    
    return Arrays.asList(errorCodeMapping, errorCodeMapping1, errorCodeMapping2);
  }
  
}
