package com.paytm.acquirer.netc.DiffService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytm.acquirer.netc.TestingConditions;
import com.paytm.acquirer.netc.config.properties.RedisTtlConfig;
import com.paytm.acquirer.netc.config.properties.TimeoutProperties;
import com.paytm.acquirer.netc.db.entities.AsyncTransaction;
import com.paytm.acquirer.netc.db.repositories.master.AsyncTransactionMasterRepository;
import com.paytm.acquirer.netc.dto.common.HeaderXml;
import com.paytm.acquirer.netc.dto.common.QueryExceptionResponseXml;
import com.paytm.acquirer.netc.dto.queryException.RespQueryExceptionListXml;
import com.paytm.acquirer.netc.enums.Status;
import com.paytm.acquirer.netc.exception.NetcEngineException;
import com.paytm.acquirer.netc.kafka.producer.KafkaProducer;
import com.paytm.acquirer.netc.service.AsyncTxnService;
import com.paytm.acquirer.netc.service.CustomMetricService;
import com.paytm.acquirer.netc.service.ReminderService;
import com.paytm.acquirer.netc.service.S3ServiceWrapper;
import com.paytm.acquirer.netc.service.TagExceptionService;
import com.paytm.acquirer.netc.service.common.RedisService;
import com.paytm.acquirer.netc.service.common.SignatureService;
import com.paytm.acquirer.netc.service.retry.RetryResponseService;
import com.paytm.acquirer.netc.util.Constants;
import com.paytm.acquirer.netc.util.JsonUtil;
import com.paytm.acquirer.netc.util.XmlUtil;
import com.paytm.transport.kafka.TransportKafkaTemplate;
import com.paytm.transport.metrics.DataDogClient;
import com.paytm.transport.service.StorageService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.paytm.acquirer.netc.CommonTestFunction.DUMMY_MSG_ID;
import static com.paytm.acquirer.netc.enums.ExceptionCode.BLACKLIST;
import static com.paytm.acquirer.netc.util.Constants.RESULT_FAILURE;
import static com.paytm.acquirer.netc.util.Constants.RESULT_SUCCESS;
import static com.paytm.acquirer.netc.util.Constants.SUCCESS_RESPONSE_CODE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DiffViaResponseTest {
  
  @Mock
  KafkaProducer kafkaProducer;
  @Mock
  AsyncTxnService asyncTxnService;
  @Mock
  AsyncTransactionMasterRepository asyncTransactionMasterRepository;
  @Mock
  ReminderService reminderService;
  @Mock
  SignatureService signatureService;
  @Mock
  RedisTemplate redisTemplate;
  @Mock
  ValueOperations<String, Object> valueOperations;
  @Mock
  DataDogClient dataDogClient;
  @Mock
  RedisTtlConfig redisTtlConfig;
  @Mock
  SetOperations<String, Object> setOperations;
  @Mock
  StorageService storageService;
  @Mock
  ObjectMapper objectMapper;
  @Mock
  TransportKafkaTemplate<Object> kafkaTemplate;
  
  @InjectMocks
  TimeoutProperties timeoutProperties;
  @InjectMocks
  TagExceptionService tagExceptionService;
  @InjectMocks
  RetryResponseService retryResponseService;
  @InjectMocks
  RedisService redisService;
  @InjectMocks
  CustomMetricService customMetricService;
  @InjectMocks
  S3ServiceWrapper s3ServiceWrapper;
  @InjectMocks
  KafkaProducer producer;
  
  
  @Rule
  public ExpectedException exceptionRule = ExpectedException.none();
  
  @Before
  public void setUp() throws Exception {
    TestingConditions testingConditions = new TestingConditions();
    ReflectionTestUtils.setField(s3ServiceWrapper, "storageService", storageService);
    ReflectionTestUtils.setField(s3ServiceWrapper, "objectMapper", objectMapper);
    ReflectionTestUtils.setField(tagExceptionService, "s3ServiceWrapper", s3ServiceWrapper);
    ReflectionTestUtils.setField(tagExceptionService, "timeoutProperties", timeoutProperties);
    ReflectionTestUtils.setField(tagExceptionService, "redisService", redisService);
    ReflectionTestUtils.setField(tagExceptionService, "customMetricService", customMetricService);
    ReflectionTestUtils.setField(producer, "kafkaTemplate", kafkaTemplate);
    ReflectionTestUtils.setField(tagExceptionService, "producer", producer);
    ReflectionTestUtils.setField(retryResponseService, "tagExceptionService", tagExceptionService);
    ReflectionTestUtils.setField(redisService, "redisTtlConfig", redisTtlConfig);
    ReflectionTestUtils.setField(retryResponseService, "redisService", redisService);
    ReflectionTestUtils.setField(retryResponseService, "customMetricService", customMetricService);
  
    when(signatureService.isXmlValid(any())).thenReturn(true);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(redisTemplate.opsForSet()).thenReturn(setOperations);
    when(asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(anyString(), any(), anyInt()))
      .thenReturn(Optional.of(getAsyncTransaction(testingConditions)));
    when(asyncTransactionMasterRepository.findByMsgIdAndApi(anyString(), any()))
      .thenReturn(Optional.of(getAsyncTransaction(testingConditions)));
    when(redisTtlConfig.getMsgIdTtl()).thenReturn(20);
    when(objectMapper.writeValueAsString(any())).thenReturn(new String());
  }
  
  @Test
  public void diffRespUnknownMsgIdTest() {
    TestingConditions testingConditions = new TestingConditions();
    when(asyncTransactionMasterRepository.findByMsgIdAndApi(anyString(), any())).thenReturn(Optional.empty());
    exceptionRule.expect(NetcEngineException.class);
    exceptionRule.expectMessage("Got response callback for unknown msgId ");
    retryResponseService.prepareCallbackResponseData(getRespQueryExceptionListXml(testingConditions));
  }
  
  @Test
  public void diffRespMsgTimeoutTest() {
    TestingConditions testingConditions = new TestingConditions();
    when(redisTemplate.opsForValue().get(anyString())).thenReturn(true);
    retryResponseService.prepareCallbackResponseData(getRespQueryExceptionListXml(testingConditions));
    verify(asyncTransactionMasterRepository, times(0)).findByMsgIdAndApi(anyString(), any());
  }
  
  @Test
  public void diffRespAlreadyReceivedTest() {
    TestingConditions testingConditions = new TestingConditions();
    retryResponseService.prepareCallbackResponseData(getRespQueryExceptionListXml(testingConditions));
    verify(redisTemplate, times(1)).delete(anyString());
  }
  
  @Test
  public void diffErrorResponse() {
    TestingConditions testingConditions = new TestingConditions();
    testingConditions.setDiffErrorResponse(true);
    when(asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(anyString(), any(), anyInt())).thenReturn(Optional.empty());
    retryResponseService.prepareCallbackResponseData(getRespQueryExceptionListXml(testingConditions));
    verify(redisTemplate, times(1)).delete(anyString());
  }
  
  @Test
  public void diffSuccessResponseLastMsgTest() {
    TestingConditions testingConditions = new TestingConditions();
    when(asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(anyString(), any(), anyInt())).thenReturn(Optional.empty());
    retryResponseService.prepareCallbackResponseData(getRespQueryExceptionListXml(testingConditions));
    verify(kafkaTemplate, times(1)).sendSync(any(), any(), any(), any());
  }
  
  @Test
  public void diffSuccessResponseFirstMsgTest() {
    TestingConditions testingConditions = new TestingConditions();
    testingConditions.setTotalMsgNumber(2);
    when(asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(anyString(), any(), anyInt())).thenReturn(Optional.empty());
    retryResponseService.prepareCallbackResponseData(getRespQueryExceptionListXml(testingConditions));
    verify(kafkaTemplate, times(0)).sendSync(any(), any(), any(), any());
  }
  
  @Test
  public void diffTestRequestViaApiTrueTest() {
    TestingConditions testingConditions = new TestingConditions();
    testingConditions.setTestRequestApi(true);
    when(asyncTransactionMasterRepository.findByMsgIdAndApi(anyString(), any())).thenReturn(Optional.of(getAsyncTransaction(testingConditions)));
    when(asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(anyString(), any(), anyInt())).thenReturn(Optional.empty());
    retryResponseService.prepareCallbackResponseData(getRespQueryExceptionListXml(testingConditions));
    verify(kafkaTemplate, times(0)).sendSync(any(), any(), any(), any());
  }
  
  @Test
  public void diffTestRequestViaApiAllDiffTrueTest() {
    TestingConditions testingConditions = new TestingConditions();
    testingConditions.setTestRequestApi(true);
    when(asyncTransactionMasterRepository.findByApiOrderByIdDesc(any(), any())).thenReturn(Collections.singletonList(getAsyncTransaction(testingConditions)));
    when(asyncTransactionMasterRepository.findByMsgIdAndApi(anyString(), any())).thenReturn(Optional.of(getAsyncTransaction(testingConditions)));
    when(asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(anyString(), any(), anyInt())).thenReturn(Optional.empty());
    retryResponseService.prepareCallbackResponseData(getRespQueryExceptionListXml(testingConditions));
    verify(kafkaTemplate, times(0)).sendSync(any(), any(), any(), any());
  }
  
  private String getRespQueryExceptionListXml(TestingConditions testingConditions) {
    RespQueryExceptionListXml respQueryExceptionListXml = new RespQueryExceptionListXml();
    RespQueryExceptionListXml.RespQueryExceptionTransactionXml respQueryExceptionTransactionXml = new RespQueryExceptionListXml.RespQueryExceptionTransactionXml();
    QueryExceptionResponseXml responseXml = new QueryExceptionResponseXml();
  
    responseXml.setMessageNumber(1);
    responseXml.setTotalMessage(testingConditions.getTotalMsgNumber());
    responseXml.setResponseCode(SUCCESS_RESPONSE_CODE);
    responseXml.setResult(RESULT_SUCCESS);
    
    QueryExceptionResponseXml.Exceptions exception = new QueryExceptionResponseXml.Exceptions();
    exception.setResult(RESULT_SUCCESS);
    if(testingConditions.isDiffErrorResponse()) {
      exception.setResult(RESULT_FAILURE);
    }
  
    QueryExceptionResponseXml.Tag tag = new QueryExceptionResponseXml.Tag();
    tag.setOperation("ADD");
    tag.setTagId("123456789");

    exception.setTagList(Collections.singletonList(tag));
    exception.setExceptionCode(BLACKLIST.toString());
    responseXml.setExceptionList(Collections.singletonList(exception));
    
    respQueryExceptionTransactionXml.setResponse(responseXml);
    respQueryExceptionListXml.setTransaction(respQueryExceptionTransactionXml);
    respQueryExceptionListXml.setHeader(getHeaderXml(testingConditions));
  
    return XmlUtil.serializeXmlDocument(respQueryExceptionListXml);
  }
  
  private HeaderXml getHeaderXml(TestingConditions testingConditions) {
    HeaderXml headerXml = new HeaderXml();
    headerXml.setMessageId(DUMMY_MSG_ID);
    return headerXml;
  }
  
  private AsyncTransaction getAsyncTransaction(TestingConditions testingConditions) {
    AsyncTransaction asyncTransaction = new AsyncTransaction();
    asyncTransaction.setRefId("REF12345");
    asyncTransaction.setStatus(Status.REQUEST_OK);
    asyncTransaction.setCreatedAt(Timestamp.valueOf(LocalDateTime.now().minusSeconds(10)));
    asyncTransaction.setMetaData(JsonUtil.serialiseJson(Collections.singletonMap(Constants.UID, "test")));
    if(testingConditions.isTestRequestApi()) {
      asyncTransaction.setMetaData(JsonUtil.serialiseJson(Collections.singletonMap(Constants.TEST_REQUEST_VIA_API, "true")));
    }
    return asyncTransaction;
  }
  
}
