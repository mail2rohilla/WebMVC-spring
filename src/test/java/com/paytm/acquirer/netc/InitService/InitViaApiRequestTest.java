package com.paytm.acquirer.netc.InitService;

import com.paytm.acquirer.netc.config.properties.RetryProperties;
import com.paytm.acquirer.netc.config.properties.TimeoutProperties;
import com.paytm.acquirer.netc.dto.getException.PassManagerEndpoint;
import com.paytm.acquirer.netc.enums.NetcEndpoint;
import com.paytm.acquirer.netc.enums.Status;
import com.paytm.acquirer.netc.exception.NetcEngineException;
import com.paytm.acquirer.netc.service.AsyncTxnService;
import com.paytm.acquirer.netc.service.InitService;
import com.paytm.acquirer.netc.service.PassService;
import com.paytm.acquirer.netc.service.ReminderService;
import com.paytm.acquirer.netc.service.common.MetadataService;
import com.paytm.acquirer.netc.service.common.NetcClient;
import com.paytm.acquirer.netc.service.common.RedisService;
import com.paytm.acquirer.netc.service.common.RestTemplateService;
import com.paytm.acquirer.netc.service.common.SignatureService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class InitViaApiRequestTest {

  @Mock
  RedisService redisService;
  @Mock
  SignatureService signatureService;
  @Mock
  AsyncTxnService asyncTxnService;
  @Mock
  NetcClient netcClient;
  @Mock
  ReminderService reminderService;
  @Mock
  RestTemplateService restTemplateService;

  @InjectMocks
  TimeoutProperties timeoutProperties;
  @InjectMocks
  MetadataService metadataService;
  @InjectMocks
  RetryProperties retryProperties;
  @InjectMocks
  PassService passService;
  @InjectMocks
  InitService initService;

  @Captor
  ArgumentCaptor<Status> requestStatusCaptor;

  @Before
  public void setUp() throws Exception {
    ReflectionTestUtils.setField(initService, "retryProperties", retryProperties);
    ReflectionTestUtils.setField(initService, "metadataService", metadataService);
    ReflectionTestUtils.setField(initService, "timeoutProperties", timeoutProperties);
    ReflectionTestUtils.setField(initService, "passService", passService);
    ReflectionTestUtils.setField(passService, "passManagerEndpoint", getPassManagerEndpoint());
    ReflectionTestUtils.setField(metadataService, "redisService", redisService);
    ReflectionTestUtils.setField(metadataService, "orgId", "TEST-ORG");

    when(signatureService.signXmlDocument(anyString())).thenReturn("");

  }

  @Test
  public void sendRequestToFetchInitDataTest() {
    initService.getExceptionList(0);

    verify(asyncTxnService, times(1)).updateEntry(
      anyString(), requestStatusCaptor.capture(), anyString(), eq(NetcEndpoint.GET_EXCEPTION_LIST));
    Assert.assertEquals(Status.REQUEST_OK, requestStatusCaptor.getValue());
  }

  @Test
  public void sendRequestToFetchInitDataAndCheckStatusCodeTest() {
    ResponseEntity<Void> responseEntity = new ResponseEntity<>(HttpStatus.OK);

    when(netcClient.requestGetExceptionList(anyString())).thenReturn(responseEntity);

    initService.getExceptionList(0);

    verify(asyncTxnService, times(1)).updateEntry(
      anyString(), requestStatusCaptor.capture(), anyString(), eq(NetcEndpoint.GET_EXCEPTION_LIST));
    Assert.assertEquals(Status.REQUEST_OK, requestStatusCaptor.getValue());
  }

  @Test
  public void fetchRequestInitDataAndMaxRetriesExceededTest() {
    initService.getExceptionList(2);

    verify(redisService, times(1)).removeInitInProgressFlag();
  }

  @Test
  public void sendRequestToFetchInitDataFailedTest() {
    when(netcClient.requestGetExceptionList(anyString())).thenThrow(NetcEngineException.class);

    initService.getExceptionList(0);

    verify(asyncTxnService, times(1)).updateEntry(
      anyString(), requestStatusCaptor.capture(), anyString(), eq(NetcEndpoint.GET_EXCEPTION_LIST));
    Assert.assertEquals(Status.REQUEST_FAILED, requestStatusCaptor.getValue());
  }

  @Test
  public void preloadInitDAtaInitDataFailedTest() {
    when(netcClient.requestExceptionHandlerToPreloadTagsData(anyString())).thenThrow(NetcEngineException.class);

    initService.getExceptionList(0);

    verify(asyncTxnService, times(1)).updateEntry(
      anyString(), requestStatusCaptor.capture(), anyString(), eq(NetcEndpoint.GET_EXCEPTION_LIST));
    Assert.assertEquals(Status.REQUEST_OK, requestStatusCaptor.getValue());
  }

  @Test
  public void sendRequestToFetchInitDataFailedUseToServerErrorTest() {
    HttpStatusCodeException exception = new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
    when(netcClient.requestGetExceptionList(anyString())).thenThrow(exception);

    initService.getExceptionList(0);

    verify(asyncTxnService, times(1)).updateEntry(
      anyString(), requestStatusCaptor.capture(), anyString(), eq(NetcEndpoint.GET_EXCEPTION_LIST));
    Assert.assertEquals(Status.REQUEST_FAILED, requestStatusCaptor.getValue());
  }

  private PassManagerEndpoint getPassManagerEndpoint() {
    PassManagerEndpoint passManagerEndpoint = new PassManagerEndpoint();
    passManagerEndpoint.setBaseUrl("localhost");
    passManagerEndpoint.setSaveActivePassForInit("savePass");
    return passManagerEndpoint;
  }
  
}
