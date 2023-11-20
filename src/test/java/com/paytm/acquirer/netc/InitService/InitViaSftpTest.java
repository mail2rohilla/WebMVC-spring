package com.paytm.acquirer.netc.InitService;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import com.paytm.acquirer.netc.dto.getException.ExceptionFileXml;
import com.paytm.acquirer.netc.dto.getException.SFTPConfig;
import com.paytm.acquirer.netc.dto.kafka.ExceptionMessage;
import com.paytm.acquirer.netc.exception.NetcEngineException;
import com.paytm.acquirer.netc.kafka.producer.KafkaProducer;
import com.paytm.acquirer.netc.service.InitSftpService;
import com.paytm.acquirer.netc.service.StorageServiceWrapper;
import com.paytm.acquirer.netc.service.common.FileService;
import com.paytm.acquirer.netc.service.common.RedisService;
import com.paytm.acquirer.netc.service.common.SftpService;
import com.paytm.acquirer.netc.service.common.ValidateService;
import com.paytm.acquirer.netc.util.Constants;
import com.paytm.acquirer.netc.util.SFTPUtil;
import com.paytm.acquirer.netc.util.Utils;
import com.paytm.acquirer.netc.util.XmlUtil;
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
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class InitViaSftpTest {
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
  KafkaProducer kafkaProducer;
  @Mock
  StorageServiceWrapper storageServiceWrapper;

  @InjectMocks
  ValidateService validateService;
  @InjectMocks
  @Spy
  InitSftpService initSftpService;

  @Captor ArgumentCaptor<String> stringArgumentCaptor;
  @Captor ArgumentCaptor<ExceptionMessage> exceptionMessageArgumentCaptor;

  private final String dateFormat = "dd-MM-yyyy HH:mm:ss";

  @Before
  public void setUp() throws Exception {
    ReflectionTestUtils.setField(sftpService, "sftpConfig", getSftpConfig());
    ReflectionTestUtils.setField(initSftpService, "sftpConfig", getSftpConfig());
    ReflectionTestUtils.setField(initSftpService, "sftpService", sftpService);
    ReflectionTestUtils.setField(initSftpService, "validateService", validateService);

    when(sftpService.createSession()).thenReturn(session);
    when(sftpService.creatSFTPChannel(session)).thenReturn(channelSftp);
    when(sftpService.getFileNamesOnSFTP(any(), anyString())).thenReturn(getSftpFileNames());
    when(fileService.readFile(anyString())).thenAnswer(i -> getEncryptedFileContent(i.getArgument(0)));
    when(storageServiceWrapper.saveFile(any(), anyString())).thenAnswer(i -> i.getArgument(0));
    doReturn(10L).when(initSftpService).getWaitTime(anyInt());
    when(redisService.getInitTagsExceptionCodesFromMap(anyString(), anyList())).thenAnswer(e -> {
      ArrayList<String> tagIdList = e.getArgument(1);

      return tagIdList.stream().map(i -> null).collect(Collectors.toList());
    });
  }

  @Test
  public void fetchInitViaSftpAndSendMsgToKafkaTest() throws NoSuchFieldException, IllegalAccessException {

    initSftpService.pollExceptionFilesTag(false);
    verify(session, times(1)).disconnect();
    verify(kafkaProducer, times(1)).sendExceptionListKeys(exceptionMessageArgumentCaptor.capture());
    verify(redisService, times(10)).saveGetExceptionData(stringArgumentCaptor.capture(), any());

    List<String> setNames = stringArgumentCaptor.getAllValues();
    int counter = 1;
    System.out.println(setNames);
    for(String setName : setNames)
      Assert.assertEquals(Constants.INIT_EXCEPTION_SET + SFTPUtil.getMessageId() + "_" +  (counter++), setName);

    ExceptionMessage message = exceptionMessageArgumentCaptor.getValue();

    Assert.assertEquals(Constants.INIT_EXCEPTION_SET + SFTPUtil.getMessageId() + "_", message.getExceptionSetKeyPrefix());
    Assert.assertEquals(message.getTotalTagSetsInRedis(), new Integer(10));
  }

  @Test
  public void fetchInitViaSftpWithResetFlagAndSendMsgToKafkaTest() {

    initSftpService.pollExceptionFilesTag(true);

    verify(session, times(1)).disconnect();
    verify(kafkaProducer, times(1)).sendExceptionListKeys(any());
  }

  @Test
  public void whenInitIsCompletedViaApiBeforeFileProcessing() {

    when(redisService.isInitCompletionFlagExists()).thenReturn(true);

    initSftpService.pollExceptionFilesTag(false);
    verify(sftpService, times(0)).createSession();
    verify(session, times(0)).disconnect();
  }

  @Test
  public void whenInitIsCompletedViaApiAfterFileProcessing() {
    when(redisService.isInitCompletionFlagExists()).thenReturn(false).thenReturn(true);

    initSftpService.pollExceptionFilesTag(false);

    verify(sftpService, times(1)).createSession();
    verify(kafkaProducer, times(0)).sendExceptionListKeys(any());
    verify(session, times(1)).disconnect();
  }

  @Test
  public void fetchInitViaSftpWhenSessionIsNotCreatedTest() {

    when(sftpService.createSession()).thenReturn(null);
    initSftpService.pollExceptionFilesTag(false);

    verify(session, times(0)).disconnect();
  }

  @Test
  public void fetchInitViaSftpWhenNoFilesExistsTest() {

    when(sftpService.getFileNamesOnSFTP(any(), anyString())).thenReturn(Collections.emptyList());
    initSftpService.pollExceptionFilesTag(false);

    verify(sftpService, times(1)).createSession();
    verify(kafkaProducer, times(0)).sendExceptionListKeys(any());
    verify(session, times(1)).disconnect();
  }

  @Test
  public void errorWhenFetchingFilesFromSftpTest() {
    when(sftpService.getFileNamesOnSFTP(any(), anyString()))
      .thenReturn(getSftpFileNames()).thenThrow(NetcEngineException.class);

    initSftpService.pollExceptionFilesTag(false);

    verify(sftpService, times(1)).createSession();
    verify(kafkaProducer, times(0)).sendExceptionListKeys(any());
    verify(session, times(1)).disconnect();
  }

  @Test
  public void fetchInitViaSftpAndInvalidFileDataCountTest() throws IOException {

    when(fileService.readFile(anyString())).thenAnswer(i -> getInvalidHeadersEncryptedFileContent(i.getArgument(0)));

    initSftpService.pollExceptionFilesTag(false);

    verify(sftpService, times(1)).createSession();
    verify(kafkaProducer, times(0)).sendExceptionListKeys(any());
    verify(session, times(1)).disconnect();
  }

  private String generateRandomAlphaNumericString() {
    int leftLimit = 48; // numeral '0'
    int rightLimit = 122; // letter 'z'
    int targetStringLength = 10;
    Random random = new Random();

    String generatedString = random.ints(leftLimit, rightLimit + 1)
      .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
      .limit(targetStringLength)
      .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
      .toString();

   return generatedString;
  }

  private SFTPConfig getSftpConfig() {
    SFTPConfig sftpConfig = new SFTPConfig();
    sftpConfig.setRemoteDir("/fastag-data/Staging/sftp_stag_user/Exception/");
    sftpConfig.setLocalDir("logs");
    sftpConfig.setFileName("ETC-CONSL-EXCP-LIST-");
    sftpConfig.setEncryptedFolder("logs/EncryptedFiles");
    sftpConfig.setDecryptedFolder("logs/DecryptedFiles");
    sftpConfig.setHost("host");
    sftpConfig.setPort(22);
    sftpConfig.setUsername("username");
    sftpConfig.setPassword("password");
    return sftpConfig;
  }

  private List<String> getSftpFileNames() {
    List<String> sftpFileNames = new ArrayList<>();

    sftpFileNames.add(getSftpFileName(1));
    sftpFileNames.add(getSftpFileName(2));
    sftpFileNames.add(getSftpFileName(3));
    sftpFileNames.add(getSftpFileName(4));
    sftpFileNames.add(getSftpFileName(5));
    return sftpFileNames;
  }

  private String getSftpFileName(Integer count) {

    String prefix = "ETC-CONSL-EXCP-LIST-";
    String postfix = "-00";
    return prefix + LocalDate.now() + "-" + postfix + "5" + "-" + postfix + count + Constants.CSV_EXT;
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
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append(Utils.getTodayDate()).append(",100000,").append(fileNameData.get(10), 0, 3);
    stringBuilder.append("\nTagId,ExceptionListCode,Updated Time");
    for(int i=0; i< 100000; i++){
      int code = ((int)(Math.random()*6) + 1);
      stringBuilder.append("\n34161FA820328E400D" + generateRandomAlphaNumericString() +  ",0" + code + ",");
      stringBuilder.append(LocalDateTime.now().plusSeconds(10).format(DateTimeFormatter.ofPattern(dateFormat)));
    }
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
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append(Utils.getTodayDate()).append(",4,").append(fileNameData.get(9), 0, 3);
    stringBuilder.append("\nTagId,ExceptionListCode,Updated Time");
    stringBuilder.append("\n34161FA820328E400D4464E0,03,");
    stringBuilder.append(LocalDateTime.now().plusSeconds(10).format(DateTimeFormatter.ofPattern(dateFormat)));

    return stringBuilder.toString();
  }

  @After
  public void afterClass() {
    FileUtils.deleteQuietly(new File(getSftpConfig().getEncryptedFolder()));
    FileUtils.deleteQuietly(new File(getSftpConfig().getDecryptedFolder()));
  }

}
