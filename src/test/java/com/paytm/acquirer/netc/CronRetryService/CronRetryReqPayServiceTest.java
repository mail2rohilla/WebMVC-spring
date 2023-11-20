package com.paytm.acquirer.netc.CronRetryService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytm.acquirer.netc.CommonTestFunction;
import com.paytm.acquirer.netc.KafkaAck;
import com.paytm.acquirer.netc.TestingConditions;
import com.paytm.acquirer.netc.adapter.KafkaAdapter;
import com.paytm.acquirer.netc.config.properties.RetryProperties;
import com.paytm.acquirer.netc.config.properties.TimeoutProperties;
import com.paytm.acquirer.netc.db.entities.AsyncTransaction;
import com.paytm.acquirer.netc.db.entities.ErrorCodeMapping;
import com.paytm.acquirer.netc.db.repositories.master.AsyncTransactionMasterRepository;
import com.paytm.acquirer.netc.db.repositories.master.ErrorCodeMappingMasterRepository;
import com.paytm.acquirer.netc.dto.common.VehicleDetails;
import com.paytm.acquirer.netc.dto.cron.CronResponse;
import com.paytm.acquirer.netc.dto.pay.ReqPay;
import com.paytm.acquirer.netc.dto.pay.ReqPayXml;
import com.paytm.acquirer.netc.dto.retry.RetryDto;
import com.paytm.acquirer.netc.dto.transactionstatus.RespCheckTransactionXml;
import com.paytm.acquirer.netc.enums.HandlerType;
import com.paytm.acquirer.netc.enums.NetcEndpoint;
import com.paytm.acquirer.netc.enums.PlazaCategory;
import com.paytm.acquirer.netc.enums.PlazaType;
import com.paytm.acquirer.netc.enums.RetrialType;
import com.paytm.acquirer.netc.enums.Status;
import com.paytm.acquirer.netc.enums.TransactionType;
import com.paytm.acquirer.netc.kafka.consumer.KafkaConsumer;
import com.paytm.acquirer.netc.kafka.producer.KafkaProducer;
import com.paytm.acquirer.netc.service.AsyncTxnService;
import com.paytm.acquirer.netc.service.CheckTransactionService;
import com.paytm.acquirer.netc.service.CustomMetricService;
import com.paytm.acquirer.netc.service.ReminderService;
import com.paytm.acquirer.netc.service.ReqPayService;
import com.paytm.acquirer.netc.service.ReqPayTollService;
import com.paytm.acquirer.netc.service.RespPayService;
import com.paytm.acquirer.netc.service.TimeService;
import com.paytm.acquirer.netc.service.common.MetadataService;
import com.paytm.acquirer.netc.service.common.NetcClient;
import com.paytm.acquirer.netc.service.common.RedisService;
import com.paytm.acquirer.netc.service.common.RestTemplateService;
import com.paytm.acquirer.netc.service.common.SignatureService;
import com.paytm.acquirer.netc.service.retry.CronRetryService;
import com.paytm.acquirer.netc.util.Constants;
import com.paytm.acquirer.netc.util.JsonUtil;
import com.paytm.acquirer.netc.util.Utils;
import com.paytm.acquirer.netc.util.XmlUtil;
import com.paytm.transport.metrics.DataDogClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.paytm.acquirer.netc.CommonTestFunction.DUMMY_MSG_ID;
import static com.paytm.acquirer.netc.CommonTestFunction.DUMMY_MSG_ID_MAX_RETRY;
import static com.paytm.acquirer.netc.CommonTestFunction.DUMMY_TAG_ID;
import static com.paytm.acquirer.netc.CommonTestFunction.DUMMY_TRANSACTION_ID;
import static com.paytm.acquirer.netc.CommonTestFunction.MAX_RETRY;
import static com.paytm.acquirer.netc.util.Constants.RESULT_FAILURE;
import static com.paytm.acquirer.netc.util.Constants.ReqPay.TXN_PENDING;
import static com.paytm.acquirer.netc.util.Constants.SUCCESS_RESPONSE_CODE;
import static com.paytm.acquirer.netc.util.Constants.TIME_SYNC_ERROR_CODE;
import static com.paytm.acquirer.netc.util.Constants.TRANSACTION_ACCEPTED;
import static com.paytm.acquirer.netc.util.Constants.TRANSACTION_DECLINED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CronRetryReqPayServiceTest {
  
  @Mock
  private RestTemplateService restTemplateService;
  @Mock
  private AsyncTransactionMasterRepository asyncTransactionMasterRepository;
  @Mock
  private DataDogClient dataDogClient;
  @Mock
  private ValueOperations<String, Object> valueOperations;
  @Mock
  private RedisTemplate<String, Object> redisTemplate;
  @Mock
  private KafkaProducer kafkaProducer;
  @Mock
  private ErrorCodeMappingMasterRepository errorCodeMappingRepository;
  @Mock
  private TimeService timeService;
  @Mock
  private ReminderService reminderService;
  
  @InjectMocks
  private NetcClient netcClient;
  @InjectMocks
  private RetryProperties retryLimit;
  @InjectMocks
  private CheckTransactionService checkTransactionService;
  @InjectMocks
  private RedisService redisService;
  @InjectMocks
  private AsyncTxnService asyncTxnService;
  @InjectMocks
  private ObjectMapper mapper;
  @InjectMocks
  private MetadataService metadataService;
  @InjectMocks
  private KafkaConsumer kafkaConsumer;
  @InjectMocks
  private CronRetryService retryService;
  @InjectMocks
  private CustomMetricService customMetricService;
  @InjectMocks
  private ReqPayService reqPayService;
  @InjectMocks
  private RespPayService respPayService;
  @InjectMocks
  private TimeoutProperties timeoutProperties;
  
  SignatureService signatureService = CommonTestFunction.getSignatureService();
  
  @Before
  public void setUp() {
    ReflectionTestUtils.setField(metadataService, "redisService", redisService);
    ReflectionTestUtils.setField(metadataService, "orgId", "TEST-ORG");
    ReflectionTestUtils.setField(checkTransactionService, "metadataService", metadataService);
    ReflectionTestUtils.setField(checkTransactionService, "netcClient", netcClient);
    ReflectionTestUtils.setField(checkTransactionService, "signatureService", signatureService);
    ReflectionTestUtils.setField(checkTransactionService, "retryProperties", retryLimit);
    ReflectionTestUtils.setField(retryService, "customMetricService", customMetricService);
    ReflectionTestUtils.setField(retryService, "asyncTxnService", asyncTxnService);
    ReflectionTestUtils.setField(retryService, "retryLimit", retryLimit);
    ReflectionTestUtils.setField(retryService, "checkTransactionService", checkTransactionService);
    ReflectionTestUtils.setField(retryService, "retryProperties", retryLimit);
    ReflectionTestUtils.setField(retryService, "respPayService", respPayService);
    ReflectionTestUtils.setField(retryService, "timeoutProperties", timeoutProperties);
    ReflectionTestUtils.setField(retryService, "errorCodeMappingRepository", errorCodeMappingRepository);
    ReflectionTestUtils.setField(kafkaConsumer, "retryService", retryService);
    ReflectionTestUtils.setField(kafkaConsumer, "mapper", mapper);
    ReflectionTestUtils.setField(kafkaConsumer, "reqPayService", reqPayService);
  
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(errorCodeMappingRepository.findByHandlerNotNull()).thenReturn(getErrorCodeList());
  }
  
  @Test
  public void CronRetryResponsePayAlreadyReceivedTest () {
    TestingConditions conditions = new TestingConditions();
    when(asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(anyString(), eq(NetcEndpoint.RESP_PAY), anyInt()))
      .thenReturn(Optional.of(getAsyncTransaction()));
    kafkaConsumer.processRetryFlow(getCronResponseDto(conditions), new KafkaAck());
    verify(kafkaProducer, times(0)).sendReqPayRetryMsg(any());
  }
  
  @Test
  public void CronRetryCircuitBreakerTrueTest () {
    TestingConditions conditions = new TestingConditions();
    conditions.setRetryType(RetrialType.CIRCUIT_BREAKER_RETRY);
    kafkaConsumer.processRetryFlow(getCronResponseDto(conditions), new KafkaAck());
    verify(kafkaProducer, times(1)).sendReqPayRetryMsg(any());
  }
  
  @Test
  public void cronRetryAutoRetryTest () {
    TestingConditions conditions = new TestingConditions();
    conditions.setRetryType(RetrialType.AUTO_RETRYABLE_CODE_RETRY);
    kafkaConsumer.processRetryFlow(getCronResponseDto(conditions), new KafkaAck());
    verify(kafkaProducer, times(1)).notifyTxnManagerToRetryReqPay(any());
  }
  
  @Test
  public void CronRetryCheckTransactionNullRetryTest () {
    TestingConditions conditions = new TestingConditions();
    when(restTemplateService.executePostRequestReturnResponseEntity(anyString(), eq(String.class), anyString(),
      any(), eq(null))).thenReturn(new ResponseEntity<String>(HttpStatus.OK));
    when(asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(anyString(), eq(NetcEndpoint.RESP_PAY), anyInt()))
      .thenReturn(Optional.empty());
    kafkaConsumer.processRetryFlow(getCronResponseDto(conditions), new KafkaAck());
    verify(kafkaProducer, times(1)).sendReqPayRetryMsg(any());
  }
  
  @Test
  public void CronRetryCheckTransactionMaxRetryExceedTest () {
    TestingConditions conditions = new TestingConditions();
    conditions.setCheckTxmMaxRetryLimit(true);
    kafkaConsumer.processRetryFlow(getCronResponseDto(conditions), new KafkaAck());
    verify(kafkaProducer, times(0)).sendReqPayRetryMsg(any());
  }
  
  @Test
  public void CronRetryReqPayMaxRetryExceedTest () {
    TestingConditions conditions = new TestingConditions();
    conditions.setMaxRetryLimit(true);
    when(restTemplateService.executePostRequestReturnResponseEntity(anyString(), eq(String.class), anyString(),
      any(), eq(null))).thenReturn(new ResponseEntity<String>(HttpStatus.OK));
    when(asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(anyString(), eq(NetcEndpoint.RESP_PAY), anyInt()))
      .thenReturn(Optional.empty());
    kafkaConsumer.processRetryFlow(getCronResponseDto(conditions), new KafkaAck());
    verify(kafkaProducer, times(0)).sendReqPayRetryMsg(any());
  }
  
  @Test
  public void CronRetryCheckTransactionFoundRetryTest () {
    TestingConditions conditions = new TestingConditions();
    when(restTemplateService.executePostRequestReturnResponseEntity(anyString(), eq(String.class), anyString(),
      any(), eq(null))).thenReturn(new ResponseEntity<String>(getRespCheckTransactionXml(conditions),HttpStatus.OK));
    when(asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(anyString(), eq(NetcEndpoint.RESP_PAY), anyInt()))
      .thenReturn(Optional.empty());
    when(asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(anyString(), eq(NetcEndpoint.REQ_PAY), anyInt()))
      .thenReturn(Optional.of(getAsyncTransaction()));
    kafkaConsumer.processRetryFlow(getCronResponseDto(conditions), new KafkaAck());
    verify(kafkaProducer, times(0)).sendReqPayRetryMsg(any());
    verify(kafkaProducer, times(1)).send(any());
  }
  
  @Test
  public void CronRetryCheckTransactionTimeSyncErrorTest () {
    TestingConditions conditions = new TestingConditions();
    conditions.setTimeSyncError(true);
    conditions.setResultFailure(true);
    when(restTemplateService.executePostRequestReturnResponseEntity(anyString(), eq(String.class), anyString(),
      any(), eq(null))).thenReturn(new ResponseEntity<String>(getRespCheckTransactionXml(conditions),HttpStatus.OK));
    when(asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(anyString(), eq(NetcEndpoint.RESP_PAY), anyInt()))
      .thenReturn(Optional.empty());
    kafkaConsumer.processRetryFlow(getCronResponseDto(conditions), new KafkaAck());
    verify(kafkaProducer, times(1)).sendReqPayRetryMsg(any());
    verify(kafkaProducer, times(0)).send(any());
  }
  
  @Test
  public void CronRetryCheckTransactionTimeSync2ndRetryTest () {
    TestingConditions conditions = new TestingConditions();
    conditions.setTimeSyncError(true);
    conditions.setResultFailure(true);
    TestingConditions conditions1 = new TestingConditions();
    conditions1.setResultFailure(true);
    when(restTemplateService.executePostRequestReturnResponseEntity(anyString(), eq(String.class), anyString(), any(), eq(null)))
      .thenReturn(new ResponseEntity<String>(getRespCheckTransactionXml(conditions),HttpStatus.OK))
      .thenReturn(new ResponseEntity<String>(getRespCheckTransactionXml(conditions1),HttpStatus.OK));
    when(asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(anyString(), eq(NetcEndpoint.RESP_PAY), anyInt()))
      .thenReturn(Optional.empty());
    kafkaConsumer.processRetryFlow(getCronResponseDto(conditions), new KafkaAck());
    verify(kafkaProducer, times(1)).sendReqPayRetryMsg(any());
    verify(kafkaProducer, times(0)).send(any());
  }
  
  
  @Test
  public void CronRetryCheckTransactionEmptyTxnListTest () {
    TestingConditions conditions = new TestingConditions();
    conditions.setEmptyValidChkTxnList(true);
    when(restTemplateService.executePostRequestReturnResponseEntity(anyString(), eq(String.class), anyString(),
      any(), eq(null))).thenReturn(new ResponseEntity<String>(getRespCheckTransactionXml(conditions),HttpStatus.OK));
    when(asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(anyString(), eq(NetcEndpoint.RESP_PAY), anyInt()))
      .thenReturn(Optional.empty());
    kafkaConsumer.processRetryFlow(getCronResponseDto(conditions), new KafkaAck());
    verify(kafkaProducer, times(1)).sendReqPayRetryMsg(any());
    verify(kafkaProducer, times(0)).send(any());
  }
  
  @Test
  public void CronRetryCheckPendingTransactionFoundRetryTest () {
    TestingConditions conditions = new TestingConditions();
    conditions.setTxnStatus(TXN_PENDING);
    when(restTemplateService.executePostRequestReturnResponseEntity(anyString(), eq(String.class), anyString(),
      any(), eq(null))).thenReturn(new ResponseEntity<String>(getRespCheckTransactionXml(conditions),HttpStatus.OK));
    when(asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(anyString(), eq(NetcEndpoint.RESP_PAY), anyInt()))
      .thenReturn(Optional.empty());
    kafkaConsumer.processRetryFlow(getCronResponseDto(conditions), new KafkaAck());
    verify(kafkaProducer, times(0)).sendReqPayRetryMsg(any());
    verify(kafkaProducer, times(0)).send(any());
  }
  
  @Test
  public void CronRetryCheckDuplicate201RetryTest () {
    TestingConditions conditions = new TestingConditions();
    conditions.setTxnStatus(TRANSACTION_DECLINED);
    conditions.setResponseCode("201");
    when(restTemplateService.executePostRequestReturnResponseEntity(anyString(), eq(String.class), anyString(),
      any(), eq(null))).thenReturn(new ResponseEntity<String>(getRespCheckTransactionXml(conditions),HttpStatus.OK));
    when(asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(anyString(), eq(NetcEndpoint.RESP_PAY), anyInt()))
      .thenReturn(Optional.empty());
    when(asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(anyString(), eq(NetcEndpoint.REQ_PAY), anyInt()))
      .thenReturn(Optional.of(getAsyncTransaction()));
    kafkaConsumer.processRetryFlow(getCronResponseDto(conditions), new KafkaAck());
    verify(kafkaProducer, times(0)).sendReqPayRetryMsg(any());
    verify(kafkaProducer, times(1)).send(any());
  }
  
  @Test
  public void CronRetryCheckTransactionDeclinedAutoRetryFoundRetryTest () {
    TestingConditions conditions = new TestingConditions();
    conditions.setTxnStatus(TRANSACTION_DECLINED);
    conditions.setResponseCode("123");
    when(restTemplateService.executePostRequestReturnResponseEntity(anyString(), eq(String.class), anyString(),
      any(), eq(null))).thenReturn(new ResponseEntity<String>(getRespCheckTransactionXml(conditions),HttpStatus.OK));
    when(asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(anyString(), eq(NetcEndpoint.RESP_PAY), anyInt()))
      .thenReturn(Optional.empty());
    when(asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(anyString(), eq(NetcEndpoint.REQ_PAY), anyInt()))
      .thenReturn(Optional.of(getAsyncTransaction()));
    when(asyncTransactionMasterRepository.findByMsgIdAndApi(anyString(), any())).thenReturn(Optional.of(new AsyncTransaction()));
    kafkaConsumer.processRetryFlow(getCronResponseDto(conditions), new KafkaAck());
    verify(kafkaProducer, times(0)).sendReqPayRetryMsg(any());
    verify(kafkaProducer, times(1)).send(any());
    verify(kafkaProducer, times(1)).notifyTxnManagerToRetryReqPay(any());
  }
  
  @Test
  public void CronRetryCheckTransactionDeclinedAutoRetryMaxRetryTest () {
    TestingConditions conditions = new TestingConditions();
    conditions.setTxnStatus(TRANSACTION_DECLINED);
    conditions.setResponseCode("123");
    conditions.setMaxRetryLimit(true);
    when(restTemplateService.executePostRequestReturnResponseEntity(anyString(), eq(String.class), anyString(),
      any(), eq(null))).thenReturn(new ResponseEntity<String>(getRespCheckTransactionXml(conditions),HttpStatus.OK));
    when(asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(anyString(), eq(NetcEndpoint.RESP_PAY), anyInt()))
      .thenReturn(Optional.empty());
    when(asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(anyString(), eq(NetcEndpoint.REQ_PAY), anyInt()))
      .thenReturn(Optional.of(getAsyncTransaction()));
    when(asyncTransactionMasterRepository.findByMsgIdAndApi(anyString(), any())).thenReturn(Optional.of(new AsyncTransaction()));
    kafkaConsumer.processRetryFlow(getCronResponseDto(conditions), new KafkaAck());
    verify(kafkaProducer, times(0)).sendReqPayRetryMsg(any());
    verify(kafkaProducer, times(1)).send(any());
    verify(kafkaProducer, times(0)).notifyTxnManagerToRetryReqPay(any());
  }
  
  @Test
  public void CronRetryCheckTransactionDeclinedManualRetryFoundRetryTest () {
    TestingConditions conditions = new TestingConditions();
    conditions.setTxnStatus(TRANSACTION_DECLINED);
    conditions.setResponseCode("124");
    when(restTemplateService.executePostRequestReturnResponseEntity(anyString(), eq(String.class), anyString(),
      any(), eq(null))).thenReturn(new ResponseEntity<String>(getRespCheckTransactionXml(conditions),HttpStatus.OK));
    when(asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(anyString(), eq(NetcEndpoint.RESP_PAY), anyInt()))
      .thenReturn(Optional.empty());
    when(asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(anyString(), eq(NetcEndpoint.REQ_PAY), anyInt()))
      .thenReturn(Optional.of(getAsyncTransaction()));
    when(asyncTransactionMasterRepository.findByMsgIdAndApi(anyString(), any())).thenReturn(Optional.of(new AsyncTransaction()));
    kafkaConsumer.processRetryFlow(getCronResponseDto(conditions), new KafkaAck());
    verify(kafkaProducer, times(0)).sendReqPayRetryMsg(any());
    verify(kafkaProducer, times(1)).send(any());
    verify(kafkaProducer, times(0)).notifyTxnManagerToRetryReqPay(any());
  }
  
  @Test
  public void CronRetryCheckTransactionDeclinedAutoBlackListFoundRetryTest () {
    TestingConditions conditions = new TestingConditions();
    conditions.setTxnStatus(TRANSACTION_DECLINED);
    conditions.setResponseCode("125");
    when(restTemplateService.executePostRequestReturnResponseEntity(anyString(), eq(String.class), anyString(),
      any(), eq(null))).thenReturn(new ResponseEntity<String>(getRespCheckTransactionXml(conditions),HttpStatus.OK));
    when(asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(anyString(), eq(NetcEndpoint.RESP_PAY), anyInt()))
      .thenReturn(Optional.empty());
    when(asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(anyString(), eq(NetcEndpoint.REQ_PAY), anyInt()))
      .thenReturn(Optional.of(getAsyncTransaction()));
    kafkaConsumer.processRetryFlow(getCronResponseDto(conditions), new KafkaAck());
    verify(kafkaProducer, times(0)).sendReqPayRetryMsg(any());
    verify(kafkaProducer, times(1)).send(any());
    verify(kafkaProducer, times(0)).notifyTxnManagerToRetryReqPay(any());
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
  
  private String getCronResponseDto(TestingConditions conditions) {
    CronResponse<RetryDto> cronResponse = new CronResponse<>();
    cronResponse.setErrorMessage(SUCCESS_RESPONSE_CODE);
    cronResponse.setMessageId(DUMMY_MSG_ID);
    cronResponse.setClientOperationId(DUMMY_MSG_ID);
    cronResponse.setMessageBody(getRetryDto(conditions));
    return JsonUtil.serialiseJson(cronResponse);
  }
  
  private RetryDto getRetryDto(TestingConditions conditions) {
    ReqPayTollService iReqPayService = new ReqPayTollService();
    ReqPay reqPay = getReqPay();
    String requestTime = Utils.getFormattedDate(LocalDateTime.now().minusMinutes(1));
    ReqPayXml request = iReqPayService.convertReqPayToXml(reqPay, requestTime, metadataService);
    request.setHeader(metadataService.createXmlHeader(request.getTransaction().getId(), requestTime, DUMMY_MSG_ID, 0));
    RetryDto retryDto = KafkaAdapter.createKafkaMsgForReqPay(request, reqPay, conditions.getRetryType());
    if(conditions.isCheckTxmMaxRetryLimit()) {
      retryDto.getParams().put(Constants.ReqPay.CHECK_TXN_RETRY_COUNT, MAX_RETRY);
    }
    if(conditions.isMaxRetryLimit()) {
      retryDto.setMsgId(DUMMY_MSG_ID_MAX_RETRY);
    }
    return retryDto;
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
    reqPay.setTagId(DUMMY_TAG_ID);
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
    vehicleDetails.setTagId(DUMMY_TAG_ID);
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
  
  private AsyncTransaction getAsyncTransaction() {
    AsyncTransaction asyncTransaction = new AsyncTransaction();
    asyncTransaction.setRefId("REF12345");
    asyncTransaction.setStatus(Status.REQUEST_OK);
    asyncTransaction.setCreatedAt(Timestamp.valueOf(LocalDateTime.now().minusSeconds(10)));
    return asyncTransaction;
  }
  
  private String getRespCheckTransactionXml(TestingConditions conditions) {
    RespCheckTransactionXml respCheckTransactionXml = new RespCheckTransactionXml();
    RespCheckTransactionXml.CheckTxnTransactionXml checkTxnTransactionXml = getCheckTxnTransactionXml(conditions);
    RespCheckTransactionXml.ChkTxnRespXml chkTxnRespXml = getChkRespXml(conditions);
    
    List<RespCheckTransactionXml.StatusXml> statusList = new ArrayList<>();
    RespCheckTransactionXml.StatusXml statusXml = getStatusXml(conditions);
    
    List<RespCheckTransactionXml.TransactionListXml> transactionList = new ArrayList<>();
    RespCheckTransactionXml.TransactionListXml transactionListXml = getTransactionXml(conditions);
    transactionList.add(transactionListXml);
    
    statusXml.setTransactionList(transactionList);
    statusList.add(statusXml);
    
    chkTxnRespXml.setStatusList(statusList);
    checkTxnTransactionXml.setResponse(chkTxnRespXml);
    respCheckTransactionXml.setCheckTxnTransactionXml(checkTxnTransactionXml);
    return XmlUtil.serializeXmlDocument(respCheckTransactionXml);
  }
  
  private RespCheckTransactionXml.TransactionListXml getTransactionXml(TestingConditions conditions) {
    RespCheckTransactionXml.TransactionListXml transactionListXml = new RespCheckTransactionXml.TransactionListXml();
    transactionListXml.setPayeeErrorCode(SUCCESS_RESPONSE_CODE);
    transactionListXml.setTxnType(String.valueOf(TransactionType.DEBIT));
    transactionListXml.setTxnStatus(TRANSACTION_ACCEPTED);
    transactionListXml.setTxnReaderTime(Utils.getFormattedDate(LocalDateTime.now().minusMinutes(1)));
    transactionListXml.setTxnReceivedTime(Utils.getFormattedDate(LocalDateTime.now().minusMinutes(1)));
    if(conditions.isEmptyValidChkTxnList()) {
      transactionListXml.setTxnReaderTime(Utils.getFormattedDate(LocalDateTime.now().minusMinutes(2)));
    }

    transactionListXml.setTxnStatus(conditions.getTxnStatus());
    transactionListXml.setResponseCode(conditions.getResponseCode());
    
    return transactionListXml;
  }
  
  private RespCheckTransactionXml.StatusXml  getStatusXml(TestingConditions conditions) {
    RespCheckTransactionXml.StatusXml statusXml = new RespCheckTransactionXml.StatusXml();
    statusXml.setErrCode(SUCCESS_RESPONSE_CODE);
    statusXml.setResult(SUCCESS_RESPONSE_CODE);
    statusXml.setAcquirerId("1234");
    statusXml.setMerchantId("1234");
    statusXml.setTxnDate(Utils.getFormattedDate(LocalDateTime.now().minusMinutes(1)));
    statusXml.setTxnId(DUMMY_TRANSACTION_ID);
    if(conditions.isTimeSyncError()) {
      statusXml.setErrCode(TIME_SYNC_ERROR_CODE);
    }
    if(conditions.isResultFailure()) {
      statusXml.setResult(RESULT_FAILURE);
    }
    return statusXml;
  }
  
  private RespCheckTransactionXml.ChkTxnRespXml getChkRespXml(TestingConditions conditions) {
    RespCheckTransactionXml.ChkTxnRespXml chkTxnRespXml = new RespCheckTransactionXml.ChkTxnRespXml();
    chkTxnRespXml.setResult(SUCCESS_RESPONSE_CODE);
    chkTxnRespXml.setResponseCode(SUCCESS_RESPONSE_CODE);
    chkTxnRespXml.setSuccessRequestCount(1);
    chkTxnRespXml.setTotalRequestCount(1);
    chkTxnRespXml.setTimeStamp(Utils.getFormattedDate(LocalDateTime.now().minusMinutes(1)));
    if(conditions.isTimeSyncError()) {
      chkTxnRespXml.setResponseCode(TIME_SYNC_ERROR_CODE);
    }
    if(conditions.isResultFailure()) {
      chkTxnRespXml.setResult(RESULT_FAILURE);
    }
    return  chkTxnRespXml;
  }
  
  private RespCheckTransactionXml.CheckTxnTransactionXml getCheckTxnTransactionXml(TestingConditions conditions) {
    RespCheckTransactionXml.CheckTxnTransactionXml checkTxnTransactionXml = new RespCheckTransactionXml.CheckTxnTransactionXml();
    checkTxnTransactionXml.setType(TransactionType.DEBIT);
    checkTxnTransactionXml.setTimeStamp(Utils.getFormattedDate(LocalDateTime.now().minusMinutes(1)));
    return checkTxnTransactionXml;
  }
}
