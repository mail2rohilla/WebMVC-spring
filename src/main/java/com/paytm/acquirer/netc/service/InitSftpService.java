package com.paytm.acquirer.netc.service;

import com.google.common.collect.Iterables;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.paytm.acquirer.netc.db.entities.AsyncTransaction;
import com.paytm.acquirer.netc.db.repositories.master.AsyncTransactionMasterRepository;
import com.paytm.acquirer.netc.dto.common.ExceptionHeaderDto;
import com.paytm.acquirer.netc.dto.getException.ExceptionFileDto;
import com.paytm.acquirer.netc.dto.getException.SFTPConfig;
import com.paytm.acquirer.netc.dto.kafka.ExceptionMessage;
import com.paytm.acquirer.netc.enums.ErrorMessage;
import com.paytm.acquirer.netc.enums.ExceptionCode;
import com.paytm.acquirer.netc.enums.ListType;
import com.paytm.acquirer.netc.enums.NetcEndpoint;
import com.paytm.acquirer.netc.enums.Status;
import com.paytm.acquirer.netc.exception.NetcEngineException;
import com.paytm.acquirer.netc.kafka.producer.KafkaProducer;
import com.paytm.acquirer.netc.service.common.RedisService;
import com.paytm.acquirer.netc.service.common.SftpService;
import com.paytm.acquirer.netc.service.common.ValidateService;
import com.paytm.acquirer.netc.util.Constants;
import com.paytm.acquirer.netc.util.SFTPUtil;
import com.paytm.acquirer.netc.util.Utils;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import com.paytm.transport.util.DynamicPropertyUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.paytm.acquirer.netc.util.Constants.DynamicConfig.DefaultValue.NPCI_SFTP_FILES_PATH_VALUE;

@Service
@RequiredArgsConstructor
public class InitSftpService {

  private static final Logger log = LoggerFactory.getLogger(InitSftpService.class);

  private final KafkaProducer producer;
  private final AsyncTxnService asyncTxnService;
  private final RedisService redisService;
  private final AsyncTransactionMasterRepository asyncTransactionMasterRepository;
  private final StorageServiceWrapper storageServiceWrapper;
  private final ValidateService validateService;
  private final SFTPConfig sftpConfig;
  private final SftpService sftpService;

  @Async("exception-list-executor")
  public void pollExceptionFilesTag(boolean resetFlag) {

    log.info("Started to poll exception file from sftp at {}", LocalDateTime.now());
    long timeStart = System.currentTimeMillis();
    String fileName = sftpConfig.getFileName() + LocalDate.now();
    boolean saveS3Flag = false;
    Session session = null;
    try {
      if (resetFlag) {
        redisService.removeInitCompletionFlag();
        redisService.removeInitSFTPFlag();
      }
      if (redisService.isInitProcessedViaSftp()
        || redisService.isInitCompletionFlagExists()) {
        log.info("Init file generation is already processed for the day ");
        return;
      }
  
      Map<String, String> fileMap = new LinkedHashMap<>();
      session = sftpService.createSession();
      if (session == null) {
        log.error("Sftp session found null while creating session");
        throw new NetcEngineException("Unable to creation session");
      }

      ChannelSftp channelSftp = sftpService.creatSFTPChannel(session);
      String remoteDir = getNpciFilePath(channelSftp, fileName);
      
      List<String> sftpFiles = getNumOfFileNamesONSFTP(channelSftp, remoteDir, fileName);
      if (sftpFiles.isEmpty()) {
        log.error("No exception files found  at {} ", LocalDateTime.now());
        return;
      }
      redisService.setInitProcessedFlagViaSftp(true);
      log.info("Number of files on sftp server {}", sftpFiles.size());
      boolean isINITCompleted = transformINITFilesToFileMap(fileName, remoteDir, fileMap, channelSftp, sftpFiles);
      if (isINITCompleted) {
        return;
      }

      saveS3Flag = true;
      log.info("No of files downloaded from SftpNo of files downloaded from Sftp {} ", fileMap.size());
      log.info(
        "Time taken to download files {}",
        TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - timeStart));
      log.info(
        "Time taken before validating the SFTP files {}", LocalDateTime.now());
      validateService.validateSftpFiles(fileMap, sftpConfig.getDecryptedFolder());
      log.info("Time taken after validating the sftp files {}", LocalDateTime.now());
      validateHeaders(fileMap);
      log.info(
        "Time taken after validating the headers {}",
        LocalDateTime.now());

      AtomicReference<Timestamp> lastUpdatedTime = new AtomicReference<>();

      ExceptionMessage message = storeInitDataInRedisAndGetKafkaMessgeForException(fileMap, lastUpdatedTime);
      if (redisService.isInitCompletionFlagExists()) {
        log.info("Init file generation is already processed for the day {} ", LocalDateTime.now());
        return;
      }

      redisService.setInitCompletionFlag(true);
      markAPIRequestAsResponseRequestBySFTP();
      producer.sendExceptionListKeys(message);
    } catch (NetcEngineException ex) {
      log.error("Error while processing the init files {}", ex);
      redisService.removeInitSFTPFlag();
    } catch (Exception e) {
      log.error("Error occurred while processing the init file {} ", e);
      redisService.removeInitSFTPFlag();
      Thread.currentThread().interrupt();
    } finally {
      log.info("Save the file if it exists in the local dir");
      saveInitFilesToS3(fileName, saveS3Flag);
      if (session != null) {
        session.disconnect();
      }
    }

    long timeEnd = System.currentTimeMillis();
    log.info(
      "Time Taken to poll exception tag via sftp in minutes {} ",
      TimeUnit.MILLISECONDS.toMinutes(timeEnd - timeStart));
  }
  
  private String getNpciFilePath(ChannelSftp channelSftp, String fileName) {
    String[] possiblePath = DynamicPropertyUtil.getPropertyValue(Constants.DynamicConfigKey.NPCI_SFTP_FILES_PATH, NPCI_SFTP_FILES_PATH_VALUE).split(",");
    for (String remoteDir : possiblePath) {
      log.info("checking files on path {}", remoteDir);
      try {
        List<String> sftpFiles = getNumOfFileNamesONSFTP(channelSftp, remoteDir, fileName);
        if (!sftpFiles.isEmpty()) {
          return remoteDir;
        }
      } catch (Exception e) {
        log.error("getting error while fetching files on path {}", e, remoteDir);
      }
    }
    throw new NetcEngineException("None of the possible path have any file on NPCI sftp");
  }
  
  private boolean transformINITFilesToFileMap(String fileName, String remoteDir, Map<String, String> fileMap,
                                              ChannelSftp channelSftp, List<String> sftpFiles)
    throws SftpException, InterruptedException {
    int i = 1;
    int numOfFiles = sftpFiles.size();
    String totalFileCount = getFileSeqNumber(numOfFiles);
    String fileNameNew = "";
    try {
      while (i <= numOfFiles) {
        fileNameNew = fileName + totalFileCount + getFileSeqNumber(i) + Constants.CSV_EXT;
        String localFolSv = sftpConfig.getEncryptedFolder() + File.separator + LocalDate.now();
        log.info("create directory structure {} ", localFolSv);
        SFTPUtil.createLocalDirStructure(localFolSv);
        String localFileFullPath = localFolSv + File.separator + fileNameNew;
        channelSftp.get(remoteDir + fileNameNew, localFileFullPath);
        fileMap.put(fileNameNew, localFileFullPath);
        if (i == numOfFiles) {
          long waitingTime = getWaitTime(numOfFiles);
          log.info("buffer time taken while checking the diff {}", waitingTime);
          Thread.sleep(waitingTime);
          List<String> countFile = getNumOfFileNamesONSFTP(channelSftp, remoteDir, fileName);
          int diff = countFile.size() - numOfFiles;
          numOfFiles = numOfFiles + (Math.max(diff, 0));
        }

        if (redisService.isInitCompletionFlagExists()) {
          log.info("Init file generation is already processed for the day {}, total files fetched {}",
            LocalDate.now(), numOfFiles);
          return true;
        }
        i++;
      }
    } catch (Exception ex) {
      log.error("Unable to find the exception file with name {}",ex,  fileNameNew);
      throw ex;
    } finally {
      channelSftp.exit();
    }
    return false;
  }

  private void markAPIRequestAsResponseRequestBySFTP() {
    String apiMessageId = redisService.getInitInProgressAPIMsgId();
    if (Objects.isNull(apiMessageId)) {
      return;
    }

    Optional<AsyncTransaction> transaction = asyncTransactionMasterRepository.findByMsgIdAndApi(
      apiMessageId, NetcEndpoint.GET_EXCEPTION_LIST);
    if (transaction.isPresent() && transaction.get().getStatus().equals(Status.REQUEST_OK)) {
      asyncTxnService.updateEntry(apiMessageId, Status.RESPONSE_RECEIVED_SFTP, NetcEndpoint.GET_EXCEPTION_LIST);
    }
  }

  private ExceptionMessage storeInitDataInRedisAndGetKafkaMessgeForException(Map<String, String> fileMap, AtomicReference<Timestamp> lastUpdatedTime) {
    List<String> uniqueTagList = new ArrayList<>();
    AtomicInteger setCounter = new AtomicInteger(1);
    String initMapKey = Constants.INIT_EXCEPTION_MAP + SFTPUtil.getMessageId();
    String initSetKeyPrefix = Constants.INIT_EXCEPTION_SET + SFTPUtil.getMessageId() + "_";

    for (Map.Entry<String, String> entry : fileMap.entrySet()) {
      StringBuilder decryptedFullPath = new StringBuilder();
      String locFileName = entry.getKey();
      decryptedFullPath.append(sftpConfig.getDecryptedFolder()).append(File.separator)
        .append(LocalDate.now()).append(File.separator).append(locFileName);

      try (Stream<String> stream = Files.lines(Paths.get(decryptedFullPath.toString()))) {

        Iterables.partition(stream.skip(2).collect(Collectors.toList()), 50000).forEach(strings -> {
          Map<String, Integer> tagExceptionCodeSumMap = new HashMap<>();
          strings.forEach(line -> {
            ExceptionFileDto exceptionFileDto = SFTPUtil.convertToObject(line, ExceptionFileDto.class, ",");

            Integer exceptionCodeSum = tagExceptionCodeSumMap.getOrDefault(exceptionFileDto.getTagId(), 0);
            Integer exceptionValue = ExceptionCode.getBinaryValue(exceptionFileDto.getExceptionCode());
            if (Objects.isNull(exceptionValue)) {
              return;
            }

            tagExceptionCodeSumMap.put(exceptionFileDto.getTagId(), exceptionCodeSum | exceptionValue);

            if (Objects.isNull(lastUpdatedTime.get()) ||
              lastUpdatedTime.get().before(exceptionFileDto.getUpdatedTime())) {
              lastUpdatedTime.set(exceptionFileDto.getUpdatedTime());
            }
          });

          log.info("Batch Size {}", tagExceptionCodeSumMap.size());

          List<String> tagIdList = new ArrayList<>(tagExceptionCodeSumMap.keySet());
          List<Integer> existingExceptionCodeSumList = redisService.getInitTagsExceptionCodesFromMap(initMapKey, tagIdList);
          IntStream.range(0, tagIdList.size())
            .forEach(index -> {
              Integer exceptionValue = existingExceptionCodeSumList.get(index);
              if (Objects.isNull(exceptionValue)) {
                uniqueTagList.add(tagIdList.get(index));
                return;
              }

              Integer exceptionCodeSum = tagExceptionCodeSumMap.get(tagIdList.get(index)) | exceptionValue;
              tagExceptionCodeSumMap.put(tagIdList.get(index), exceptionCodeSum);
            });

          redisService.saveGetExceptionDataMap(initMapKey, tagExceptionCodeSumMap);
          int sftpBatchFixedSize = DynamicPropertyUtil.getIntPropertyValue(Constants.SFTP_INIT_SET_BATCH_FIXED_SIZE_KEY,
            Constants.DynamicConfig.DefaultValue.SFTP_INIT_SET_BATCH_FIXED_SIZE);
          if(uniqueTagList.size() >= sftpBatchFixedSize){
            redisService.saveGetExceptionData(initSetKeyPrefix+ (setCounter.getAndIncrement()), uniqueTagList);
            log.debug("tags added in set {} : {}", initSetKeyPrefix+ (setCounter.get()-1),
              uniqueTagList.size());
            uniqueTagList.clear();
          }
          log.info("Time taken After saving it to redis {}, Max updated date {} ", LocalDateTime.now(), lastUpdatedTime.get());

        });

        log.info("[PollExceptionTags] the latest updated time {}", lastUpdatedTime);

      } catch (FileNotFoundException e) {
        log.error(
          "File not found error while validating the header for file {}",e, entry.getValue());
        throw new NetcEngineException(ErrorMessage.FILE_NOT_FOUND);
      } catch (IOException e) {
        log.error("Error occurred while reading header for file {}",e , entry.getValue());
        throw new NetcEngineException(ErrorMessage.FILE_NOT_FOUND);
      }
    }

    if(!uniqueTagList.isEmpty()){
      redisService.saveGetExceptionData(initSetKeyPrefix+ (setCounter.getAndIncrement()), uniqueTagList);
      uniqueTagList.clear();
    }
     return ExceptionMessage.builder()
      .type(ListType.INIT_V2)
      .msgId(SFTPUtil.getMessageId())
      .lastSuccessTime(lastUpdatedTime.get().toLocalDateTime().format(Utils.dateTimeFormatter))
      .exceptionMapKey(Constants.INIT_EXCEPTION_MAP + SFTPUtil.getMessageId())
      .exceptionSetKeyPrefix(Constants.INIT_EXCEPTION_SET + SFTPUtil.getMessageId() + "_")
      .totalTagSetsInRedis(setCounter.get()-1)
      .build();
  }


  private List<String> getNumOfFileNamesONSFTP(ChannelSftp channelSftp, String remoteDir, String fileName) {
    return sftpService.getFileNamesOnSFTP(channelSftp, remoteDir + fileName + "-*" + Constants.CSV_EXT);
  }

  private void saveInitFilesToS3(String fileName, boolean isS3UploadRequired) {

    if (!isS3UploadRequired) return;
    String currTime = Timestamp.valueOf(LocalDateTime.now()).getTime() + "-";

    createZipFileAndSaveToS3(
      sftpConfig.getEncryptedFolder() + Constants.SLASH_DELIMETER + LocalDate.now(),
      sftpConfig.getEncryptedFolder()
        + Constants.SLASH_DELIMETER
        + LocalDate.now()
        + Constants.SLASH_DELIMETER
        + Constants.ENC_FILENAME_PREFIX
        + currTime
        + fileName);
    createZipFileAndSaveToS3(
      sftpConfig.getDecryptedFolder() + Constants.SLASH_DELIMETER + LocalDate.now(),
      sftpConfig.getDecryptedFolder()
        + Constants.SLASH_DELIMETER
        + LocalDate.now()
        + Constants.SLASH_DELIMETER
        + Constants.DEC_FILENAME_PREFIX
        + currTime
        + fileName);
  }

  private String getFileSeqNumber(int i) {
    StringBuilder seqStr = new StringBuilder(Constants.HYPEN_STR);
    seqStr.append(String.format("%03d", i));
    return seqStr.toString();
  }

  private boolean validateCsvData(
    ExceptionHeaderDto exceptionHeaderDto, long count, String filename) {
    LocalDate fileCreationDate =
      LocalDate.parse(
        exceptionHeaderDto.getFileCreationDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    LocalDate date = LocalDate.now();
    String fileNameWithoutExt = FilenameUtils.removeExtension(filename);
    int indexStart = fileNameWithoutExt.lastIndexOf('-');
    int indexEnd = fileNameWithoutExt.length();

    if (fileCreationDate.compareTo(date) != 0) {
      log.error("[SFTP] : date in headers {} doesnot match with current date {}", fileCreationDate, date);
      return false;
    } else if (Long.parseLong(exceptionHeaderDto.getFileRecords()) != count) {
      log.error("[SFTP] : lineCount in headers {} doesnot match with line count in file {}", exceptionHeaderDto.getFileRecords(), count);
      return false;
    } else return Integer.valueOf(fileNameWithoutExt.substring(++indexStart, indexEnd)).intValue() ==
      Integer.valueOf(exceptionHeaderDto.getFileCount()).intValue();

  }

  private File uploadSFTPFileToS3(File file, String fileName) throws IOException {
    return storageServiceWrapper.saveFile(file, fileName);
  }

  private void createZipFileAndSaveToS3(String filePath, String filename) {
    try (Stream<Path> paths = Files.walk(Paths.get(filePath))) {
      List<File> files = paths.filter(Files::isRegularFile)
        .map(Path::toFile)
        .collect(Collectors.toList());
      if (files.isEmpty()) {
        log.info("No files found at filePath {}", filePath);
        return;
      }
      File file = SFTPUtil.createZipFile(files, filename);
      if (file == null) {
        log.error("[createZipFileAndSaveToS3] Error while creating zip file");
        throw new IOException("Error while creating zip file");
      }
      File savedFile = uploadSFTPFileToS3(file, file.getName());
      if (savedFile == null)
        log.warn("[createZipFileAndSaveToS3] Unable tp save file to S3");

      SFTPUtil.cleanDirectory(new File(filePath));
    } catch (Exception ex) {
      log.error("Error while zipping the file under path folder and saving it to s3 {} ", ex, filePath);
    }
  }

  private void validateHeaders(Map<String, String> fileMap) {
    for (Map.Entry<String, String> entry : fileMap.entrySet()) {
      String fullName = sftpConfig.getDecryptedFolder() + File.separator +
        LocalDate.now() + File.separator + entry.getKey();
      try (BufferedReader reader = new BufferedReader(new FileReader(fullName));
           Stream<String> stream = Files.lines(Paths.get(fullName))) {
        String headers = reader.readLine();
        ExceptionHeaderDto exceptionHeaderDto =
          SFTPUtil.convertToObject(headers, ExceptionHeaderDto.class, ",");

        long count = stream.skip(2L).count();

        log.info("Total records {}", count);
        if (!validateCsvData(exceptionHeaderDto, count, fullName)) {
          log.error("Header validation error for the file {} with record {} and date as {}",
            entry.getKey(), exceptionHeaderDto.getFileCount(),
            exceptionHeaderDto.getFileCreationDate());
          throw new NetcEngineException("Header validation failure");
        }
      } catch (IOException ex) {
        log.error("[Validate Headers], Error occurred while reading header for file {}", fullName);
        throw new NetcEngineException(ErrorMessage.FILE_NOT_FOUND);
      }
    }
  }

  public long getWaitTime(int count) {
    if (count <= 3) {
      return 30000;
    } else {
      return 20000;
    }
  }
}
