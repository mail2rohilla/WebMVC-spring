package com.paytm.acquirer.netc.DiffService;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import com.paytm.acquirer.netc.config.properties.RetryProperties;
import com.paytm.acquirer.netc.config.properties.TimeoutProperties;
import com.paytm.acquirer.netc.dto.getException.ExceptionFileXml;
import com.paytm.acquirer.netc.dto.getException.SFTPConfig;
import com.paytm.acquirer.netc.dto.queryException.ReqQueryExceptionDto;
import com.paytm.acquirer.netc.kafka.producer.KafkaProducer;
import com.paytm.acquirer.netc.service.AsyncTxnService;
import com.paytm.acquirer.netc.service.CustomMetricService;
import com.paytm.acquirer.netc.service.DiffService;
import com.paytm.acquirer.netc.service.ReminderService;
import com.paytm.acquirer.netc.service.S3ServiceWrapper;
import com.paytm.acquirer.netc.service.TagExceptionService;
import com.paytm.acquirer.netc.service.common.CommonService;
import com.paytm.acquirer.netc.service.common.FileService;
import com.paytm.acquirer.netc.service.common.MetadataService;
import com.paytm.acquirer.netc.service.common.NetcClient;
import com.paytm.acquirer.netc.service.common.RedisService;
import com.paytm.acquirer.netc.service.common.SftpService;
import com.paytm.acquirer.netc.service.common.SignatureService;
import com.paytm.acquirer.netc.service.common.ValidateService;
import com.paytm.acquirer.netc.util.Constants;
import com.paytm.acquirer.netc.util.Utils;
import com.paytm.acquirer.netc.util.XmlUtil;
import com.paytm.transport.metrics.DataDogClient;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DiffViaSftpTest {

  @Mock
  Session session;
  @Mock
  ChannelSftp channelSftp;
  @Mock
  RedisService redisService;
  @Mock
  SftpService sftpService;
  @Mock
  FileService fileService;
  @Mock
  S3ServiceWrapper s3ServiceWrapper;
  @Mock
  KafkaProducer kafkaProducer;
  @Mock
  SignatureService signatureService;
  @Mock
  AsyncTxnService asyncTxnService;
  @Mock
  NetcClient netcClient;
  @Mock
  ReminderService reminderService;
  @Mock
  TimeoutProperties timeoutProperties;
  @Mock
  DataDogClient dataDogClient;

  @InjectMocks
  CustomMetricService customMetricService;
  @InjectMocks
  MetadataService metadataService;
  @InjectMocks
  RetryProperties retryProperties;
  @InjectMocks
  ValidateService validateService;
  @InjectMocks
  CommonService commonService;
  @Spy
  @InjectMocks
  TagExceptionService tagExceptionService;
  @InjectMocks
  DiffService diffService;

  @Captor
  ArgumentCaptor<ReqQueryExceptionDto> reqQueryExceptionCaptor;

  private final String diffDateFormat = "dd-MM-yyyy HH:mm:ss";

  @Before
  public void setUp() throws Exception {
    ReflectionTestUtils.setField(diffService, "validateService", validateService);
    ReflectionTestUtils.setField(diffService, "sftpConfig", getSftpConfig());
    ReflectionTestUtils.setField(diffService, "sftpService", sftpService);
    ReflectionTestUtils.setField(diffService, "tagExceptionService", tagExceptionService);
    ReflectionTestUtils.setField(diffService, "commonService", commonService);
    ReflectionTestUtils.setField(sftpService, "sftpConfig", getSftpConfig());
    ReflectionTestUtils.setField(validateService, "sftpConfig", getSftpConfig());
    ReflectionTestUtils.setField(validateService, "commonService", commonService);
    ReflectionTestUtils.setField(tagExceptionService, "retryProperties", retryProperties);
    ReflectionTestUtils.setField(tagExceptionService, "validateService", validateService);
    ReflectionTestUtils.setField(tagExceptionService, "metadataService", metadataService);
    ReflectionTestUtils.setField(tagExceptionService, "tasNpciTimeDiff", 1L);
    ReflectionTestUtils.setField(commonService, "sftpConfig", getSftpConfig());
    ReflectionTestUtils.setField(metadataService, "redisService", redisService);
    ReflectionTestUtils.setField(metadataService, "orgId", "TEST-ORG");
    ReflectionTestUtils.setField(tagExceptionService, "customMetricService", customMetricService);

    when(redisService.isInitInProgress()).thenReturn(false);
    when(sftpService.createSession()).thenReturn(session);
    when(sftpService.creatSFTPChannel(session)).thenReturn(channelSftp);
    when(sftpService.getFileNamesOnSFTP(any(), anyString())).thenReturn(getSftpFileNames());
    when(fileService.readFile(anyString())).thenAnswer(i -> getEncryptedFileContent((String) i.getArguments()[0]));
  }

  @Test
  public void fetchDiffViaSftpAndSendMsgToKafkaTest() {
    ReqQueryExceptionDto reqQueryExceptionDto = getReqQueryException();

    diffService.fetchExceptionDiffFilesTag(reqQueryExceptionDto);

    verify(tagExceptionService, times(1))
      .queryExceptionList(anyInt(), reqQueryExceptionCaptor.capture());
    Assert.assertTrue(reqQueryExceptionCaptor.getValue().isTestRequestViaAPI());
  }

  @Test
  public void forceFetchDiffViaSftpAndSendMsgToKafkaTest() {
    ReqQueryExceptionDto reqQueryExceptionDto = getReqQueryException();
    reqQueryExceptionDto.setForceUseSftpForDiff(true);

    diffService.fetchExceptionDiffFilesTag(reqQueryExceptionDto);

    verify(tagExceptionService, times(0)).queryExceptionList(anyInt(), any());
  }

  @Test
  public void fetchDiffViaSftpWhenInitInProgressTest() {
    ReqQueryExceptionDto reqQueryExceptionDto = getReqQueryException();

    when(redisService.isInitInProgress()).thenReturn(true);

    diffService.fetchExceptionDiffFilesTag(reqQueryExceptionDto);

    verify(sftpService, times(0)).createSession();
  }

  @Test
  public void fetchDiffViaSftpWhenUidIsMissingTest() {
    ReqQueryExceptionDto reqQueryExceptionDto = getReqQueryException();
    reqQueryExceptionDto.setUid(null);

    diffService.fetchExceptionDiffFilesTag(reqQueryExceptionDto);

    verify(sftpService, times(0)).createSession();
  }

  @Test
  public void fetchDiffViaSftpWhenLastUpdatedTimeIsMissingTest() {
    ReqQueryExceptionDto reqQueryExceptionDto = getReqQueryException();
    reqQueryExceptionDto.setLastUpdatedTime(null);

    diffService.fetchExceptionDiffFilesTag(reqQueryExceptionDto);

    verify(sftpService, times(0)).createSession();
  }

  @Test
  public void fetchDiffViaSftpWhenLastUpdateIsLessThen24HrsTest() {
    ReqQueryExceptionDto reqQueryExceptionDto = getReqQueryException();
    reqQueryExceptionDto.setLastUpdatedTime(Utils.getFormattedDateWithoutOffset(LocalDateTime.now().minusHours(25)));

    diffService.fetchExceptionDiffFilesTag(reqQueryExceptionDto);

    verify(sftpService, times(0)).createSession();
  }

  @Test
  public void fetchDiffViaSftpWhenSessionIsNotCreatedTest() {
    ReqQueryExceptionDto reqQueryExceptionDto = getReqQueryException();

    when(sftpService.createSession()).thenReturn(null);
    diffService.fetchExceptionDiffFilesTag(reqQueryExceptionDto);

    verify(session, times(0)).disconnect();
  }

  @Test
  public void fetchDiffViaSftpAndInvalidFileHeadersTest() throws IOException {
    ReqQueryExceptionDto reqQueryExceptionDto = getReqQueryException();

    when(fileService.readFile(anyString())).thenAnswer(i ->
      getInvalidHeadersEncryptedFileContent((String) i.getArguments()[0]));

    diffService.fetchExceptionDiffFilesTag(reqQueryExceptionDto);

    verify(redisService, times(0)).saveQueryExceptionData(anyString(), any());
  }

  private ReqQueryExceptionDto getReqQueryException() {
    ReqQueryExceptionDto reqQueryExceptionDto = new ReqQueryExceptionDto();
    reqQueryExceptionDto.setLastUpdatedTime(Utils.getFormattedDateWithoutOffset(LocalDateTime.now().minusMinutes(3)));
    reqQueryExceptionDto.setUid("123456");
    return reqQueryExceptionDto;
  }

  private SFTPConfig getSftpConfig() {
    SFTPConfig sftpConfig = new SFTPConfig();
    sftpConfig.setRemoteDir("/fastag-data/Staging/sftp_stag_user/Exception/");
    sftpConfig.setLocalDir("logs");
    sftpConfig.setDiffFileName("ETC-INCR-EXCP-LIST-");
    sftpConfig.setEncryptedDiffFolder("logs/EncryptedDiffFiles");
    sftpConfig.setDecryptedDiffFolder("logs/DecryptedDiffFiles");
    sftpConfig.setHost("host");
    sftpConfig.setPort(22);
    sftpConfig.setUsername("username");
    sftpConfig.setPassword("password");
    return sftpConfig;
  }

  private List<String> getSftpFileNames() {
    List<String> sftpFileNames = new ArrayList<>();

    sftpFileNames.add(getSftpFileName(LocalDateTime.now().plusMinutes(2), LocalDateTime.now().plusMinutes(4).minusSeconds(1)));
    sftpFileNames.add(getSftpFileName(LocalDateTime.now(), LocalDateTime.now().plusMinutes(2).minusSeconds(1)));
    sftpFileNames.add(getSftpFileName(LocalDateTime.now().minusMinutes(2), LocalDateTime.now().minusSeconds(1)));
    sftpFileNames.add(getSftpFileName(LocalDateTime.now().minusMinutes(4), LocalDateTime.now().minusMinutes(2).minusSeconds(1)));
    sftpFileNames.add(getSftpFileName(LocalDateTime.now().minusMinutes(6), LocalDateTime.now().minusMinutes(4).minusSeconds(1)));
    return sftpFileNames;
  }

  private String getSftpFileName(LocalDateTime fromTime, LocalDateTime toTime) {

    String prefix = "ETC-INCR-EXCP-LIST-";
    String postfix = "-001";
    String fromTimeStr = fromTime.format(DateTimeFormatter.ofPattern(Constants.NETC_DIFF_DATE_TIME_FORMAT));
    String toTimeStr = toTime.format(DateTimeFormatter.ofPattern(Constants.NETC_DIFF_DATE_TIME_FORMAT));
    return prefix + fromTimeStr + "-" + toTimeStr + postfix + Constants.CSV_EXT;
  }

  private byte[] getEncryptedFileContent(String fileName) {
    String fileContent = getFileContent(fileName);
    ExceptionFileXml exceptionFileXml = new ExceptionFileXml();
    exceptionFileXml.setOrgContent(Base64.getEncoder().encodeToString(fileContent.getBytes()));
    return XmlUtil.serializeXmlDocument(exceptionFileXml).getBytes();
  }

  private String getFileContent(String fileName) {
    if(StringUtils.isEmpty(fileName)) {
      return fileName;
    }

    List<String> fileNameData = Arrays.asList(fileName.split("-"));
    LocalDateTime fileNameFromDateTime =
      LocalDateTime.parse(fileNameData.get(6), DateTimeFormatter.ofPattern(Constants.NETC_DIFF_DATE_TIME_FORMAT));
    LocalDateTime fileNameToDateTime =
      LocalDateTime.parse(fileNameData.get(7), DateTimeFormatter.ofPattern(Constants.NETC_DIFF_DATE_TIME_FORMAT));
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append(fileNameFromDateTime.format(DateTimeFormatter.ofPattern(diffDateFormat))).append(",");
    stringBuilder.append(fileNameToDateTime.format(DateTimeFormatter.ofPattern(diffDateFormat))).append(",4,001");
    stringBuilder.append("\nTagId,ExceptionListCode,Operation,Updated Time");
    stringBuilder.append("\n34161FA820328E400D4464E0,03,ADD,");
    stringBuilder.append(fileNameFromDateTime.plusSeconds(10).format(DateTimeFormatter.ofPattern(diffDateFormat)));
    stringBuilder.append("\n34161FA820328E400D4464E1,03,REMOVE,");
    stringBuilder.append(fileNameFromDateTime.plusSeconds(20).format(DateTimeFormatter.ofPattern(diffDateFormat)));
    stringBuilder.append("\n34161FA820328E400D4464E2,01,ADD,");
    stringBuilder.append(fileNameFromDateTime.plusSeconds(30).format(DateTimeFormatter.ofPattern(diffDateFormat)));
    stringBuilder.append("\n34161FA820328E400D4464E3,01,REMOVE,");
    stringBuilder.append(fileNameFromDateTime.plusSeconds(40).format(DateTimeFormatter.ofPattern(diffDateFormat)));
    return stringBuilder.toString();
  }

  private byte[] getInvalidHeadersEncryptedFileContent(String fileName) {
    String fileContent = getInvalidHeadersFileContent(fileName);
    ExceptionFileXml exceptionFileXml = new ExceptionFileXml();
    exceptionFileXml.setOrgContent(Base64.getEncoder().encodeToString(fileContent.getBytes()));
    return XmlUtil.serializeXmlDocument(exceptionFileXml).getBytes();
  }

  private String getInvalidHeadersFileContent(String fileName) {
    List<String> fileNameData = Arrays.asList(fileName.split("-"));
    LocalDateTime fileNameFromDateTime =
      LocalDateTime.parse(fileNameData.get(6), DateTimeFormatter.ofPattern(Constants.NETC_DIFF_DATE_TIME_FORMAT));
    LocalDateTime fileNameToDateTime =
      LocalDateTime.parse(fileNameData.get(7), DateTimeFormatter.ofPattern(Constants.NETC_DIFF_DATE_TIME_FORMAT));
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append(fileNameFromDateTime.minusSeconds(1).format(DateTimeFormatter.ofPattern(diffDateFormat))).append(",");
    stringBuilder.append(fileNameToDateTime.format(DateTimeFormatter.ofPattern(diffDateFormat))).append(",1,001");
    stringBuilder.append("\nTagId,ExceptionListCode,Operation,Updated Time");
    stringBuilder.append("\n34161FA820328E400D4464E0,03,ADD,");
    stringBuilder.append(fileNameFromDateTime.plusSeconds(10).format(DateTimeFormatter.ofPattern(diffDateFormat)));
    return stringBuilder.toString();
  }

  @After
  public void afterClass() {
    FileUtils.deleteQuietly(new File(getSftpConfig().getEncryptedDiffFolder()));
    FileUtils.deleteQuietly(new File(getSftpConfig().getDecryptedDiffFolder()));
  }

}
