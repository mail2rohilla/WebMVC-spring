package com.paytm.acquirer.netc.CronRetryService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytm.acquirer.netc.KafkaAck;
import com.paytm.acquirer.netc.TestingConditions;
import com.paytm.acquirer.netc.adapter.KafkaAdapter;
import com.paytm.acquirer.netc.config.properties.RedisTtlConfig;
import com.paytm.acquirer.netc.config.properties.RetryProperties;
import com.paytm.acquirer.netc.config.properties.TimeoutProperties;
import com.paytm.acquirer.netc.db.entities.AsyncTransaction;
import com.paytm.acquirer.netc.db.repositories.master.AsyncTransactionMasterRepository;
import com.paytm.acquirer.netc.dto.cron.CronResponse;
import com.paytm.acquirer.netc.dto.retry.RetryDto;
import com.paytm.acquirer.netc.enums.NetcEndpoint;
import com.paytm.acquirer.netc.enums.Status;
import com.paytm.acquirer.netc.kafka.consumer.KafkaConsumer;
import com.paytm.acquirer.netc.service.AsyncTxnService;
import com.paytm.acquirer.netc.service.CustomMetricService;
import com.paytm.acquirer.netc.service.InitService;
import com.paytm.acquirer.netc.service.TagExceptionService;
import com.paytm.acquirer.netc.service.common.MetadataService;
import com.paytm.acquirer.netc.service.common.RedisService;
import com.paytm.acquirer.netc.service.retry.CronRetryService;
import com.paytm.acquirer.netc.util.JsonUtil;
import com.paytm.transport.metrics.DataDogClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

import static com.paytm.acquirer.netc.CommonTestFunction.DUMMY_MSG_ID;
import static com.paytm.acquirer.netc.CommonTestFunction.DUMMY_MSG_ID_MAX_RETRY;
import static com.paytm.acquirer.netc.util.Constants.SUCCESS_RESPONSE_CODE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CronRetryExceptionTest {

  @Mock
  private AsyncTransactionMasterRepository asyncTransactionMasterRepository;
  @Mock
  private ValueOperations<String, Object> valueOperations;
  @Mock
  private RedisTemplate<String, Object> redisTemplate;
  @Mock
  private RedisTtlConfig redisTtlConfig;
  @Mock
  private InitService initService;
  @Mock
  private TagExceptionService tagExceptionService;
  @Mock
  private DataDogClient dataDogClient;

  @InjectMocks
  private RetryProperties retryLimit;
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
  private TimeoutProperties timeoutProperties;
  
  @Before
  public void setUp() {
    ReflectionTestUtils.setField(metadataService, "redisService", redisService);
    ReflectionTestUtils.setField(metadataService, "orgId", "TEST-ORG");
    ReflectionTestUtils.setField(redisService, "redisTtlConfig", redisTtlConfig);
    ReflectionTestUtils.setField(retryService, "customMetricService", customMetricService);
    ReflectionTestUtils.setField(retryService, "asyncTxnService", asyncTxnService);
    ReflectionTestUtils.setField(retryService, "retryLimit", retryLimit);
    ReflectionTestUtils.setField(retryService, "retryProperties", retryLimit);
    ReflectionTestUtils.setField(retryService, "timeoutProperties", timeoutProperties);
    ReflectionTestUtils.setField(retryService, "redisService", redisService);
    ReflectionTestUtils.setField(kafkaConsumer, "retryService", retryService);
    ReflectionTestUtils.setField(kafkaConsumer, "mapper", mapper);
    
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
  }
  
  @Test
  public void CronRetryInitTest () {
    TestingConditions conditions = new TestingConditions();
    when(asyncTransactionMasterRepository.findByMsgIdAndApi(anyString(), any())).thenReturn(Optional.of(new AsyncTransaction()));
    kafkaConsumer.processRetryFlow(getCronResponseDto(conditions), new KafkaAck());
    verify(initService, times(1)).getExceptionList(anyInt(), any());
  }
  
  @Test
  public void CronRetryInitResponseReceivedTest () {
    TestingConditions conditions = new TestingConditions();
    when(asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(anyString(), eq(NetcEndpoint.RESP_GET_EXCEPTION_LIST), anyInt())).thenReturn(Optional.of(new AsyncTransaction()));
    kafkaConsumer.processRetryFlow(getCronResponseDto(conditions), new KafkaAck());
    verify(initService, times(0)).getExceptionList(anyInt(), any());
  }
  
  @Test
  public void CronRetryInitCompletedSftpFlowTest () {
    TestingConditions conditions = new TestingConditions();
    when(redisTemplate.opsForValue().get(any())).thenReturn(true);
    when(asyncTransactionMasterRepository.findByMsgIdAndApi(anyString(), any())).thenReturn(Optional.of(new AsyncTransaction()));
    kafkaConsumer.processRetryFlow(getCronResponseDto(conditions), new KafkaAck());
    verify(initService, times(0)).getExceptionList(anyInt(), any());
  }
  
  @Test
  public void CronRetryInitMaxRetryTest () {
    TestingConditions conditions = new TestingConditions();
    conditions.setMaxRetryLimit(true);
    when(asyncTransactionMasterRepository.findByMsgIdAndApi(anyString(), any())).thenReturn(Optional.of(new AsyncTransaction()));
    kafkaConsumer.processRetryFlow(getCronResponseDto(conditions), new KafkaAck());
    verify(initService, times(0)).getExceptionList(anyInt(), any());
  }
  
  @Test
  public void CronRetryEmptyDiffTest() {
    TestingConditions conditions = new TestingConditions();
    conditions.setDiffDto(true);
    when(asyncTransactionMasterRepository.findByMsgIdAndApi(anyString(), any())).thenReturn(Optional.of(getAsyncTransaction()));
    kafkaConsumer.processRetryFlow(getCronResponseDto(conditions), new KafkaAck());
    verify(tagExceptionService, times(1)).generateEmptyQueryExceptionData(any(), any(), anyBoolean(), any());
  }
  
  @Test
  public void CronRetryDiffRequestNotPresentTest() {
    TestingConditions conditions = new TestingConditions();
    conditions.setDiffDto(true);
    when(asyncTransactionMasterRepository.findByMsgIdAndApi(anyString(), any())).thenReturn(Optional.empty());
    kafkaConsumer.processRetryFlow(getCronResponseDto(conditions), new KafkaAck());
    verify(tagExceptionService, times(0)).generateEmptyQueryExceptionData(any(), any(), anyBoolean(), any());
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
    RetryDto retryDto = null;
    if(conditions.isDiffDto()) {
      retryDto = KafkaAdapter.queryExceptionKafkaMsg(DUMMY_MSG_ID, "1", "1", "0", "!23", false);
    } else {
      retryDto = KafkaAdapter.getExceptionKafkaMsg(DUMMY_MSG_ID, "1", "1", "0");
    }
    if(conditions.isMaxRetryLimit()) {
      retryDto.setMsgId(DUMMY_MSG_ID_MAX_RETRY);
    }
    return retryDto;
  }
  
  private AsyncTransaction getAsyncTransaction() {
    AsyncTransaction asyncTransaction = new AsyncTransaction();
    asyncTransaction.setRefId("REF12345");
    asyncTransaction.setStatus(Status.REQUEST_OK);
    asyncTransaction.setCreatedAt(Timestamp.valueOf(LocalDateTime.now().minusSeconds(10)));
    return asyncTransaction;
  }
  
  
}
