package com.paytm.acquirer.netc.InitService;

import com.paytm.acquirer.netc.config.properties.TimeoutProperties;
import com.paytm.acquirer.netc.db.entities.AsyncTransaction;
import com.paytm.acquirer.netc.db.repositories.master.AsyncTransactionMasterRepository;
import com.paytm.acquirer.netc.dto.common.GetExceptionResponseXml;
import com.paytm.acquirer.netc.dto.common.HeaderXml;
import com.paytm.acquirer.netc.dto.getException.RespGetExceptionListXml;
import com.paytm.acquirer.netc.enums.ExceptionCode;
import com.paytm.acquirer.netc.enums.NetcEndpoint;
import com.paytm.acquirer.netc.enums.Status;
import com.paytm.acquirer.netc.exception.NetcEngineException;
import com.paytm.acquirer.netc.kafka.producer.KafkaProducer;
import com.paytm.acquirer.netc.service.AsyncTxnService;
import com.paytm.acquirer.netc.service.ReminderService;
import com.paytm.acquirer.netc.service.TagExceptionService;
import com.paytm.acquirer.netc.service.common.RedisService;
import com.paytm.acquirer.netc.service.retry.RetryResponseService;
import io.lettuce.core.ScriptOutputType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static com.paytm.acquirer.netc.enums.ErrorMessage.GET_EXCEPTION_CALLBACK_TIMED_OUT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class InitViaApiResponseTest {

  @Mock
  KafkaProducer kafkaProducer;
  @Mock
  AsyncTxnService asyncTxnService;
  @Mock
  AsyncTransactionMasterRepository asyncTransactionMasterRepository;
  @Mock
  RedisService redisService;
  @Mock
  ReminderService reminderService;

  @InjectMocks
  TimeoutProperties timeoutProperties;
  @InjectMocks
  TagExceptionService tagExceptionService;
  @InjectMocks
  RetryResponseService retryResponseService;

  @Captor
  ArgumentCaptor<Status> requestStatusCaptor;

  @Captor
  ArgumentCaptor<String> stringArgumentCaptor;

  @Rule
  public ExpectedException exceptionRule = ExpectedException.none();

  private static final int totalMessages = 2;
  private static final int tagsInEachCode = 10;
  private static final String msgId = "MSG000016193120000";

  @Before
  public void setUp() throws Exception {
    ReflectionTestUtils.setField(retryResponseService, "tagExceptionService", tagExceptionService);
    ReflectionTestUtils.setField(tagExceptionService, "timeoutProperties", timeoutProperties);

    when(redisService.isMsgIdTimedOut(anyString())).thenReturn(false);
    when(asyncTxnService.findByMsgIdAndApi(anyString(), any())).thenReturn(Optional.of(getAsyncTransaction()));
    when(redisService.isInitCompletionFlagExists()).thenReturn(false);
    when(asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(anyString(), any(), anyInt()))
      .thenReturn(Optional.empty());
  }

  @Test
  public void responseWithFirstMsgNumFromNpciTest() {
    retryResponseService.handleInitCallBackData(getRespGetExceptionListXml(1));

    verify(asyncTransactionMasterRepository, times(1)).save(any());
    verify(redisService, times(1)).saveGetExceptionData(anyString(), any());
    verify(reminderService, times(1)).addReminder(any(), eq(null), anyLong());
  }

  @Test
  public void responseWithLastMsgNumFromNpciTest() {
    retryResponseService.handleInitCallBackData(getRespGetExceptionListXml(totalMessages));

    verify(asyncTransactionMasterRepository, times(1)).save(any());
    verify(redisService, times(1)).saveGetExceptionData(anyString(), any());
    verify(kafkaProducer, times(1)).sendExceptionListKeys(any());
    verify(redisService, times(1)).setInitCompletionFlag(true);
  }

  @Test
  public void whenMsgIdIsTimedOutTest() {
    when(redisService.isMsgIdTimedOut(anyString())).thenReturn(true);
    exceptionRule.expect(NetcEngineException.class);
    exceptionRule.expectMessage(GET_EXCEPTION_CALLBACK_TIMED_OUT.getMessage());
    retryResponseService.handleGetExceptionListData(getRespGetExceptionListXml(1));

    verify(asyncTransactionMasterRepository, times(0)).save(any());
  }

  @Test
  public void whenRequestEntryMissingForMsgIdTest() {
    when(asyncTxnService.findByMsgIdAndApi(anyString(), any())).thenReturn(Optional.empty());
    retryResponseService.handleInitCallBackData(getRespGetExceptionListXml(1));
  
    verify(redisService, times(1)).purgeInitData(any());
    verify(redisService, times(1)).timeoutMsgId(any());
  }

  @Test
  public void whenInitIsAlreadyProcessedBySftChannelTest() {
    when(redisService.isInitCompletionFlagExists()).thenReturn(true);

    retryResponseService.handleInitCallBackData(getRespGetExceptionListXml(1));

    verify(asyncTransactionMasterRepository, times(0)).save(any());
  }

  @Test
  public void whenMultipleResponseForSameMsgNumTest() {
    when(asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(anyString(), any(), anyInt()))
      .thenReturn(Optional.of(getAsyncTransaction()));

    retryResponseService.handleInitCallBackData(getRespGetExceptionListXml(1));

    verify(asyncTransactionMasterRepository, times(0)).save(any());
    verify(redisService, times(1)).purgeInitData(any());
    verify(redisService, times(1)).timeoutMsgId(any());
  }

  @Test
  public void whenResultIsNotValidForMsgIdTest() {
    RespGetExceptionListXml respGetExceptionListXml = getRespGetExceptionListXml(1);
    respGetExceptionListXml.getTransaction().getResponse().setResult("FAILED");
    retryResponseService.handleInitCallBackData(respGetExceptionListXml);

    verify(asyncTransactionMasterRepository, times(1)).save(any());
    verify(redisService, times(1)).purgeInitData(any());
    verify(asyncTxnService, times(1)).updateEntry(
      anyString(), requestStatusCaptor.capture(), eq(NetcEndpoint.GET_EXCEPTION_LIST));
    Assert.assertEquals(Status.REQUEST_FAILED, requestStatusCaptor.getValue());
  }

  @Test
  public void whenInitIsAlreadyProcessedInLastMsgNumTest() {
    when(redisService.isInitCompletionFlagExists()).thenReturn(false).thenReturn(true);

    retryResponseService.handleInitCallBackData(getRespGetExceptionListXml(totalMessages));

    verify(asyncTransactionMasterRepository, times(1)).save(any());
    verify(redisService, times(1)).saveGetExceptionData(anyString(), any());
    verify(kafkaProducer, times(0)).sendExceptionListKeys(any());
    verify(redisService, times(0)).setInitCompletionFlag(true);
    verify(asyncTxnService, times(1)).updateEntry(
      anyString(), requestStatusCaptor.capture(), eq(NetcEndpoint.GET_EXCEPTION_LIST));
    Assert.assertEquals(Status.RESPONSE_RECEIVED_SFTP, requestStatusCaptor.getValue());
  }

  @Test
  public void responseWithFirstMsgNumFromNpciTestWithBrokenSetFormat() {
    retryResponseService.handleInitCallBackData(getRespGetExceptionListXml(1));

    verify(asyncTransactionMasterRepository, times(1)).save(any());
    verify(redisService, times(1)).saveGetExceptionData(stringArgumentCaptor.capture(), any());
    verify(reminderService, times(1)).addReminder(any(), eq(null), anyLong());

    Assert.assertEquals("INIT_EXCEPTION_SET_" + msgId + "_1", stringArgumentCaptor.getValue());
  }

  private RespGetExceptionListXml getRespGetExceptionListXml(int msgNum) {
    RespGetExceptionListXml respGetExceptionListXml = new RespGetExceptionListXml();
    respGetExceptionListXml.setTransaction(getRespGetExceptionTransactionXml(msgNum));
    respGetExceptionListXml.setHeader(getHeaderXml());
    return respGetExceptionListXml;
  }

  private RespGetExceptionListXml.RespGetExceptionTransactionXml getRespGetExceptionTransactionXml(int msgNum) {
    RespGetExceptionListXml.RespGetExceptionTransactionXml respGetExceptionTransactionXml =
      new RespGetExceptionListXml.RespGetExceptionTransactionXml();
    respGetExceptionTransactionXml.setResponse(getExceptionResponseXml(msgNum));
    return respGetExceptionTransactionXml;
  }

  private GetExceptionResponseXml getExceptionResponseXml(int msgNum) {
    GetExceptionResponseXml getExceptionResponseXml = new GetExceptionResponseXml();
    getExceptionResponseXml.setTotalMessage(totalMessages);
    getExceptionResponseXml.setMessageNumber(msgNum);
    getExceptionResponseXml.setResult("SUCCESS");
    getExceptionResponseXml.setExceptionList(Arrays.asList(
      getException(ExceptionCode.HOTLIST),
      getException(ExceptionCode.EXEMPTED_VEHICLE_CLASS),
      getException(ExceptionCode.LOW_BALANCE),
      getException(ExceptionCode.BLACKLIST),
      getException(ExceptionCode.CLOSED_REPLACED),
      getException(ExceptionCode.INVALID_CARRIAGE)
    ));
    return getExceptionResponseXml;
  }

  private GetExceptionResponseXml.Exceptions getException(ExceptionCode exceptionCode) {
    GetExceptionResponseXml.Exceptions exception = new GetExceptionResponseXml.Exceptions();
    exception.setExceptionCode(exceptionCode.getValue());
    exception.setTagList(getTagsList());
    exception.setResult("SUCCESS");
    return exception;
  }

  private List<GetExceptionResponseXml.Tag> getTagsList() {
    List<GetExceptionResponseXml.Tag> tagList = new ArrayList<>();
    Random random = new Random();
    for(int i = 0; i < tagsInEachCode; i++) {
      GetExceptionResponseXml.Tag tags = new GetExceptionResponseXml.Tag();
      tags.setTagId("34161FA82032D69866000" + random.nextInt(1000));
    }
    return tagList;
  }

  private HeaderXml getHeaderXml() {
    HeaderXml headerXml = new HeaderXml();
    headerXml.setMessageId(msgId);
    return headerXml;
  }

  private AsyncTransaction getAsyncTransaction() {
    AsyncTransaction asyncTransaction = new AsyncTransaction();
    asyncTransaction.setStatus(Status.RESPONSE_RECEIVED);
    return asyncTransaction;
  }
}
