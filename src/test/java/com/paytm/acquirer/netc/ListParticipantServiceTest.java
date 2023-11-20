package com.paytm.acquirer.netc;

import com.paytm.acquirer.netc.config.properties.RedisTtlConfig;
import com.paytm.acquirer.netc.config.properties.RetryProperties;
import com.paytm.acquirer.netc.db.entities.IinInfo;
import com.paytm.acquirer.netc.db.repositories.master.IinInfoMasterRepository;
import com.paytm.acquirer.netc.db.repositories.slave.IinInfoSlaveRepository;
import com.paytm.acquirer.netc.dto.common.HeaderXml;
import com.paytm.acquirer.netc.dto.listParticipant.RespListParticipantXml;
import com.paytm.acquirer.netc.service.ListParticipantService;
import com.paytm.acquirer.netc.service.TimeService;
import com.paytm.acquirer.netc.service.common.MetadataService;
import com.paytm.acquirer.netc.service.common.NetcClient;
import com.paytm.acquirer.netc.service.common.RedisService;
import com.paytm.acquirer.netc.service.common.RestTemplateService;
import com.paytm.acquirer.netc.service.common.SignatureService;
import com.paytm.acquirer.netc.service.common.ValidateService;
import com.paytm.acquirer.netc.service.retry.WrappedNetcClient;
import com.paytm.acquirer.netc.util.XmlUtil;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static com.paytm.acquirer.netc.util.Constants.RESULT_FAILURE;
import static com.paytm.acquirer.netc.util.Constants.RESULT_SUCCESS;
import static com.paytm.acquirer.netc.util.Constants.SUCCESS_RESPONSE_CODE;
import static com.paytm.acquirer.netc.util.Constants.TIME_SYNC_ERROR_CODE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class ListParticipantServiceTest {
  
  @Mock
  private TimeService timeService;
  @Mock
  private RedisTtlConfig redisTtlConfig;
  @Mock
  SetOperations<String, Object> setOperations;
  @Mock
  private RetryProperties retryProperties;
  @Mock
  private RestTemplateService restTemplateService;
  @Mock
  private CircuitBreaker circuitBreaker;
  @Mock
  private IinInfoMasterRepository iinInfoMasterRepository;
  @Mock
  private IinInfoSlaveRepository iinInfoSlaveRepository;
  @Mock
  private RedisTemplate<String, Object> redisTemplate;
  
  @InjectMocks
  private NetcClient netcClient;
  @InjectMocks
  private WrappedNetcClient wrappedNetcClient;
  @InjectMocks
  private MetadataService metadataService;
  @InjectMocks
  private ValidateService validateService;
  @InjectMocks
  private RedisService redisService;
  @InjectMocks
  private ListParticipantService listParticipantService;
  
  @Rule
  public ExpectedException exceptionRule = ExpectedException.none();
  
  private static final String dummyMsgId = "MSG00521627885793441198153M441";
  SignatureService signatureService = CommonTestFunction.getSignatureService();
  
  @Before
  public void setUp() {
    ReflectionTestUtils.setField(netcClient, "restTemplateService", restTemplateService);
    ReflectionTestUtils.setField(wrappedNetcClient, "netcClient", netcClient);
    ReflectionTestUtils.setField(metadataService, "orgId", "TEST-ORG");
    ReflectionTestUtils.setField(redisService, "redisTemplate", redisTemplate);
    ReflectionTestUtils.setField(redisService, "redisTtlConfig", redisTtlConfig);
    ReflectionTestUtils.setField(listParticipantService, "wrappedNetcClient", wrappedNetcClient);
    ReflectionTestUtils.setField(listParticipantService, "signatureService", signatureService);
    ReflectionTestUtils.setField(listParticipantService, "metadataService", metadataService);
    ReflectionTestUtils.setField(listParticipantService, "validateService", validateService);
    ReflectionTestUtils.setField(listParticipantService, "redisService", redisService);
    Mockito.when(retryProperties.getTimeSyncMaxRetry()).thenCallRealMethod();
    Mockito.when(redisTemplate.opsForSet()).thenReturn(setOperations);
  }
  
  @Test
  public void reqListParticipantSuccessTest () {
    TestingConditions conditions = new TestingConditions();
    Mockito.when(restTemplateService.executePostRequest(anyString(), eq(String.class), anyString(),
      any(), eq(null))).thenReturn(XmlUtil.serializeXmlDocument(getRespListParticipantXml(conditions)));
    listParticipantService.requestListParticipantRetry(null);
    verify(iinInfoMasterRepository, times(1)).saveAll(anyList());
    verify(iinInfoSlaveRepository, times(1)).findAll();
    verify(redisTemplate, times(1)).delete(anyString());
  }
  
  @Test
  public void reqListParticipantFailureTest () {
    TestingConditions conditions = new TestingConditions();
    conditions.setResultFailure(true);
    Mockito.when(restTemplateService.executePostRequest(anyString(), eq(String.class), anyString(),
      any(), eq(null))).thenReturn(XmlUtil.serializeXmlDocument(getRespListParticipantXml(conditions)));
    listParticipantService.requestListParticipantRetry(null);
    verify(iinInfoMasterRepository, times(0)).saveAll(anyList());
    verify(iinInfoSlaveRepository, times(0)).findAll();
    verify(redisTemplate, times(0)).delete(anyString());
  }
  
  @Test
  public void reqListParticipantNonEmptyOldIinListTest () {
    TestingConditions conditions = new TestingConditions();
    Mockito.when(restTemplateService.executePostRequest(anyString(), eq(String.class), anyString(),
      any(), eq(null))).thenReturn(XmlUtil.serializeXmlDocument(getRespListParticipantXml(conditions)));
    Mockito.when(iinInfoSlaveRepository.findAll()).thenReturn(getIinList(conditions));
    listParticipantService.requestListParticipantRetry(null);
    verify(iinInfoMasterRepository, times(1)).saveAll(anyList());
    verify(iinInfoSlaveRepository, times(1)).findAll();
    verify(redisTemplate, times(1)).delete(anyString());
  }
  
  @Test
  public void reqListParticipantFailureTimeSyncTest () {
    TestingConditions conditions = new TestingConditions();
    conditions.setResultFailure(true);
    conditions.setTimeSyncError(true);
    Mockito.when(restTemplateService.executePostRequest(anyString(), eq(String.class), anyString(),
      any(), eq(null))).thenReturn(XmlUtil.serializeXmlDocument(getRespListParticipantXml(conditions)));
    listParticipantService.requestListParticipantRetry(null);
    verify(iinInfoMasterRepository, times(0)).saveAll(anyList());
    verify(iinInfoSlaveRepository, times(0)).findAll();
    verify(redisTemplate, times(0)).delete(anyString());
  }
  
  private List<IinInfo> getIinList(TestingConditions conditions) {
    List<IinInfo> iinInfoList = new ArrayList<>();
    IinInfo iinInfo1 = new IinInfo();
    iinInfo1.setIssuerIin("123456");
    iinInfo1.setShortCode("PAYT");
  
    IinInfo iinInfo2 = new IinInfo();
    iinInfo2.setIssuerIin("123457");
    iinInfo2.setShortCode("ICIC");
    
    iinInfoList.add(iinInfo1);
    iinInfoList.add(iinInfo2);
    return iinInfoList;
  }
  
  private RespListParticipantXml getRespListParticipantXml(TestingConditions conditions) {
    RespListParticipantXml respListParticipantXml = new RespListParticipantXml();
    HeaderXml headerXml = new HeaderXml();
    headerXml.setMessageId(dummyMsgId);
  
    RespListParticipantXml.RespListParticipantTransactionXml transactionXml= new RespListParticipantXml.RespListParticipantTransactionXml();
    RespListParticipantXml.RespListParticipantRespXml respXml = new RespListParticipantXml.RespListParticipantRespXml();
    
    respXml.setResult(RESULT_SUCCESS);
    if(conditions.isResultFailure()) {
      respXml.setResult(RESULT_FAILURE);
    }
    
    respXml.setRespCode(SUCCESS_RESPONSE_CODE);
    if(conditions.isTimeSyncError()) {
      respXml.setRespCode(TIME_SYNC_ERROR_CODE);
    }
    
    respXml.setNoOfParticipant("1");
    List<RespListParticipantXml.RespListParticipantRespXml.ParticipantXml> participantList = new ArrayList<>();
    RespListParticipantXml.RespListParticipantRespXml.ParticipantXml participantXml = new RespListParticipantXml.RespListParticipantRespXml.ParticipantXml();
    
    participantXml.setShortCode("PAYT");
    participantXml.setErrCode(SUCCESS_RESPONSE_CODE);
    participantXml.setIssuerIin("123456");
    participantXml.setAcquirerIin("112345");

    participantList.add(participantXml);
    
    respXml.setParticipantList(participantList);
    transactionXml.setResp(respXml);
    respListParticipantXml.setTransaction(transactionXml);
    respListParticipantXml.setHeader(headerXml);
    
    return respListParticipantXml;
  }
}
