package com.paytm.acquirer.netc.service;

import com.google.common.collect.Iterables;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import com.paytm.acquirer.netc.dto.common.DiffFileNameDto;
import com.paytm.acquirer.netc.dto.common.ExceptionDiffHeaderDto;
import com.paytm.acquirer.netc.dto.getException.SFTPConfig;
import com.paytm.acquirer.netc.dto.queryException.ExceptionDiffFileDto;
import com.paytm.acquirer.netc.dto.queryException.RedisTag;
import com.paytm.acquirer.netc.dto.queryException.ReqQueryExceptionDto;
import com.paytm.acquirer.netc.enums.ErrorMessage;
import com.paytm.acquirer.netc.exception.NetcEngineException;
import com.paytm.acquirer.netc.service.common.CommonService;
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
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.paytm.acquirer.netc.util.Constants.DynamicConfig.DefaultValue.NPCI_SFTP_FILES_PATH_VALUE;

@Service
@RequiredArgsConstructor
public class DiffService {
  private static final Logger log = LoggerFactory.getLogger(DiffService.class);

  private final TagExceptionService tagExceptionService;
  private final RedisService redisService;
  private final SFTPConfig sftpConfig;
  private final SftpService sftpService;
  private final ValidateService validateService;
  private final CommonService commonService;

  @Async("exception-list-executor")
  public void fetchExceptionDiffFilesTag(ReqQueryExceptionDto reqQueryExceptionDto) {
    if(validateService.isInvalidBodyOfDiffRequest(reqQueryExceptionDto, 0)) {
      return;
    }

    LocalDateTime startTime = LocalDateTime.now();
    long startTimeLong = System.currentTimeMillis();
    String messageId = SFTPUtil.getMessageId(startTime);
    log.info("Started to poll exception diff file from sftp at {} for msg id {} ", startTime, messageId);

    Session session = null;
    AtomicReference<Timestamp> lastUpdatedTime = new AtomicReference<>();
    Map<String, String> fileMap = new HashMap<>();

    try {

      DiffFileNameDto lastDiffFileNameDto = commonService.transformDiffFileNameToDto(
        reqQueryExceptionDto.getLastSftpFileName());
      
      session = sftpService.createSession();
      if (Objects.isNull(session)) {
        log.error("Diff Sftp session found null while creating session");
        throw new NetcEngineException(ErrorMessage.SFTP_SESSION_CREATION_FAILURE);
      }

      ChannelSftp channelSftp = sftpService.creatSFTPChannel(session);
      String fileNamePrefix = sftpConfig.getDiffFileName() + Utils.getFormattedDiffDate(Utils.getLocalDateTime(reqQueryExceptionDto.getLastUpdatedTime()));
      String remoteDir = getNpciFilePath(channelSftp, fileNamePrefix);

      List<String> sftpFileNames = getDiffFilesOnSFTP(channelSftp, remoteDir,
        Utils.getLocalDateTime(reqQueryExceptionDto.getLastUpdatedTime()), startTime, lastDiffFileNameDto);
      if (sftpFileNames.isEmpty()) {
        log.error("No exception files found from {} to {} for msg id {}", reqQueryExceptionDto.getLastUpdatedTime(),
          startTime, messageId);
        throw new NetcEngineException(ErrorMessage.FILE_NOT_FOUND);
      }

      log.info("Number of files on sftp server {} for msg id {}", sftpFileNames.size(), messageId);
      transformDiffFilesToFileMap(remoteDir, fileMap, channelSftp, sftpFileNames);

      log.info("Time taken to download files {} for msg id {}",
        TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - startTimeLong), messageId);

      log.info("Time taken before validating the SFTP files {} for msg id {}", LocalDateTime.now(), messageId);
      validateService.validateSftpFiles(fileMap, sftpConfig.getDecryptedDiffFolder());
      log.info("Time taken after validating the sftp files {} for msg id {}", LocalDateTime.now(), messageId);
      Map<String, String> sortedFileMap = validateService.validateDiffFilesOrdering(fileMap, lastDiffFileNameDto);
      log.info("Time taken after validating the diff files ordering {} for msg id {}", LocalDateTime.now(), messageId);
      validateService.validateDiffHeaders(sortedFileMap);
      log.info("Time taken after validating the headers {} for msg id {}", LocalDateTime.now(), messageId);

      extractDiffDataAndStoreInRedis(messageId, lastUpdatedTime, sortedFileMap);

      List<String> fileNameList = new ArrayList<>(sortedFileMap.keySet());
      String lastSftpFileName = fileNameList.get(fileNameList.size() - 1);
      tagExceptionService.processFinalDiffData(messageId, Utils.getFormattedDate(LocalDateTime.now()),
        reqQueryExceptionDto.getUid(), false, true,
        Utils.getFormattedDateWithoutOffset(lastUpdatedTime.get().toLocalDateTime()), lastSftpFileName,
        startTime, sftpFileNames.size());

    }
    catch (NetcEngineException ex) {
      log.error("Error while processing the diff files for msg id {}", ex, messageId);
      tagExceptionService.generateEmptyQueryExceptionData(messageId, reqQueryExceptionDto.getUid(),
        true, startTime);
    }
    catch (Exception e) {
      log.error("Error occurred while processing the diff file for msg id {}", e, messageId);
      tagExceptionService.generateEmptyQueryExceptionData(messageId, reqQueryExceptionDto.getUid(),
        true, startTime);
    }
    finally {
      if (Objects.nonNull(session)) {
        session.disconnect();
      }

      deleteEncryptedAndDecryptedFiles(fileMap);
      long timeEnd = System.currentTimeMillis();
      log.info("Time Taken to poll exception tag via sftp in mins {} for msg id {}",
        TimeUnit.MILLISECONDS.toMinutes(timeEnd - startTimeLong), messageId);
      
      if(BooleanUtils.isNotTrue(reqQueryExceptionDto.getForceUseSftpForDiff())) {
        // * send a test request to generate DIFF via API to check if it is up and working
        reqQueryExceptionDto.setTestRequestViaAPI(true);
        tagExceptionService.queryExceptionList(0, reqQueryExceptionDto);
      }
    }

  }

  private void deleteEncryptedAndDecryptedFiles(Map<String, String> fileMap) {
    List<String> allFiles = fileMap.keySet().stream()
      .map(key -> sftpConfig.getDecryptedDiffFolder() + File.separator + LocalDate.now() + File.separator + key)
        .collect(Collectors.toList());
    allFiles.addAll(fileMap.values());
    SFTPUtil.cleanFiles(allFiles);
  }

  private void transformDiffFilesToFileMap(String remoteDir, Map<String, String> fileMap, ChannelSftp channelSftp,
    List<String> sftpFileNames) {

    try {
      String localFolSv = sftpConfig.getEncryptedDiffFolder() + File.separator + LocalDate.now();
      log.info("create directory structure {} ", localFolSv);
      SFTPUtil.createLocalDirStructure(localFolSv);

      for(String fileName : sftpFileNames) {
        String localFileFullPath = localFolSv + File.separator + fileName;
        channelSftp.get(remoteDir + fileName, localFileFullPath);
        fileMap.put(fileName, localFileFullPath);
      }
    }
    catch (Exception ex) {
      log.error("Unable to find the exception diff files {} ", ex, sftpFileNames);
    }
    finally {
      channelSftp.exit();
    }

  }

  private void extractDiffDataAndStoreInRedis(String messageId, AtomicReference<Timestamp> lastUpdatedTime,
    Map<String, String> fileMap) {

    int bufferTime = DynamicPropertyUtil.getIntPropertyValue(Constants.DynamicConfig.Key.DIFF_FILE_BUFFER_TIME,
      Constants.DynamicConfig.DefaultValue.DIFF_FILE_BUFFER_TIME);

    for (Map.Entry<String, String> entry : fileMap.entrySet()) {
      StringBuilder decryptedFullPath = new StringBuilder();
      String locFileName = entry.getKey();
      decryptedFullPath.append(sftpConfig.getDecryptedDiffFolder()).append(File.separator)
        .append(LocalDate.now()).append(File.separator).append(locFileName);

      try (Stream<String> stream = Files.lines(Paths.get(decryptedFullPath.toString()));
           BufferedReader reader = new BufferedReader(new FileReader(decryptedFullPath.toString()))) {

        ExceptionDiffHeaderDto exceptionHeaderDto =
          SFTPUtil.convertToObject(reader.readLine(), ExceptionDiffHeaderDto.class, ",");
        LocalDateTime fileFromDateTime =
          LocalDateTime.parse(exceptionHeaderDto.getFileFromDatetime(), DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
        LocalDateTime fileToDateTime =
          LocalDateTime.parse(exceptionHeaderDto.getFileToDatetime(), DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));

        Iterables.partition(stream.skip(2).collect(Collectors.toList()), 50000).forEach(strings -> {
          List<ExceptionDiffFileDto> exceptionFileDtoList = new ArrayList<>();
          for(String line : strings) {
            ExceptionDiffFileDto exceptionFileDto = getExceptionDiffFileDto(lastUpdatedTime, bufferTime, locFileName,
              fileFromDateTime, fileToDateTime, line);

            exceptionFileDtoList.add(exceptionFileDto);
          }

          log.info("Batch Size {} & Max updated date {} for msg id {}", exceptionFileDtoList.size(),
            lastUpdatedTime, messageId);

          List<RedisTag> exceptionDataList =
            exceptionFileDtoList.stream()
              .filter(exception -> Objects.nonNull(exception.getExceptionCode()))
              .map(RedisTag::new)
              .collect(Collectors.toList());

          String redisKey = Utils.getKeyFromPrefixAndMsgId(Constants.DIFF_KEY_PREFIX, messageId);
          redisService.saveQueryExceptionData(redisKey, exceptionDataList);
          log.info("Time taken After saving it to redis {} for msg id {}", LocalDateTime.now(), messageId);
        });

        log.info("[PollExceptionTags] the latest updated time {} for msg id {}", lastUpdatedTime.get(), messageId);

      }
      catch (FileNotFoundException e) {
        log.error(
          "File not found error while validating the header for file {} for msg id {}", e, entry.getValue(), messageId);
        throw new NetcEngineException(ErrorMessage.FILE_NOT_FOUND);
      }
      catch (IOException e) {
        log.error("Error occurred while reading header for file {} for msg id {}", e, entry.getValue(), messageId);
        throw new NetcEngineException(ErrorMessage.FILE_NOT_FOUND);
      }
    }
  }

  private ExceptionDiffFileDto getExceptionDiffFileDto(AtomicReference<Timestamp> lastUpdatedTime, int bufferTime,
    String locFileName, LocalDateTime fileFromDateTime, LocalDateTime fileToDateTime, String line) {

    ExceptionDiffFileDto exceptionFileDto = SFTPUtil.convertToObject(line, ExceptionDiffFileDto.class, ",");

    if(exceptionFileDto.getUpdatedTime().before(Timestamp.valueOf(fileFromDateTime)) ||
      exceptionFileDto.getUpdatedTime().after(Timestamp.valueOf(fileToDateTime.plusSeconds(bufferTime)))) {
      log.error("Invalid tag updated time in file {} at line {} ", locFileName, exceptionFileDto);
      throw new NetcEngineException(ErrorMessage.FILE_VALIDATION_ERROR);
    }

    if(Objects.isNull(lastUpdatedTime.get()) ||
      lastUpdatedTime.get().compareTo(exceptionFileDto.getUpdatedTime()) < 0) {
      lastUpdatedTime.set(exceptionFileDto.getUpdatedTime());
    }
    return exceptionFileDto;
  }

  private List<String> getDiffFilesOnSFTP(ChannelSftp channelSftp, String remoteDir,
    LocalDateTime fromDateTime, LocalDateTime toDateTime, DiffFileNameDto lastDiffFileNameDto) {

    List<String> validSftpFileNames = new ArrayList<>();
    String fileNamePrefix = sftpConfig.getDiffFileName() + Utils.getFormattedDiffDate(fromDateTime);
    List<String> sftpFileNames = getNumOfFileNamesONSFTP(channelSftp, remoteDir, fileNamePrefix);
    if(!fromDateTime.toLocalDate().isEqual(toDateTime.toLocalDate())) {
      fileNamePrefix = sftpConfig.getDiffFileName() + Utils.getFormattedDiffDate(toDateTime);
      sftpFileNames.addAll(getNumOfFileNamesONSFTP(channelSftp, remoteDir, fileNamePrefix));
    }

    for(String filename : sftpFileNames) {
      DiffFileNameDto diffFileNameDto = commonService.transformDiffFileNameToDto(filename);

      if(diffFileNameDto.getToDatetime().compareTo(fromDateTime) < 0 ||
        diffFileNameDto.getFromDatetime().compareTo(toDateTime) > 0 ||
        (Objects.nonNull(lastDiffFileNameDto) &&
        diffFileNameDto.getFromDatetime().isEqual(lastDiffFileNameDto.getFromDatetime()) &&
        diffFileNameDto.getToDatetime().isEqual(lastDiffFileNameDto.getToDatetime()) &&
        Integer.parseInt(diffFileNameDto.getSequence()) <= Integer.parseInt(lastDiffFileNameDto.getSequence()))
      ) {
        continue;
      }
      validSftpFileNames.add(filename);
    }

    return validSftpFileNames;
  }
  
  private List<String> getNumOfFileNamesONSFTP(ChannelSftp channelSftp, String remoteDir, String fileNamePrefix) {
    return sftpService.getFileNamesOnSFTP(channelSftp, remoteDir + fileNamePrefix + "*" + Constants.CSV_EXT);
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
}
