package com.paytm.acquirer.netc.service.common;

import com.paytm.acquirer.netc.dto.common.DiffFileNameDto;
import com.paytm.acquirer.netc.dto.common.ExceptionDiffHeaderDto;
import com.paytm.acquirer.netc.dto.getException.ExceptionFileXml;
import com.paytm.acquirer.netc.dto.getException.SFTPConfig;
import com.paytm.acquirer.netc.dto.listParticipant.RespListParticipantXml;
import com.paytm.acquirer.netc.dto.queryException.ReqQueryExceptionDto;
import com.paytm.acquirer.netc.enums.ErrorMessage;
import com.paytm.acquirer.netc.exception.NetcEngineException;
import com.paytm.acquirer.netc.util.Constants;
import com.paytm.acquirer.netc.util.SFTPUtil;
import com.paytm.acquirer.netc.util.Utils;
import com.paytm.acquirer.netc.util.XmlUtil;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static com.paytm.acquirer.netc.util.Constants.SEC_IN_DAY;

@Service
@RequiredArgsConstructor
public class ValidateService {
  private static final Logger log = LoggerFactory.getLogger(ValidateService.class);

  private final RedisService redisService;
  private final SFTPConfig sftpConfig;
  private final FileService fileService;
  private final CommonService commonService;

  public boolean isInvalidBodyOfDiffRequest(ReqQueryExceptionDto reqQueryExceptionDto, int retryCount) {
    String lastUpdatedTime = reqQueryExceptionDto.getLastUpdatedTime();
    if (redisService.isInitInProgress() || redisService.isInitProcessedViaSftp()) {
      log.info("Since INIT flow is going on so we're skipping DIFF requests in between, API: {}, SFTP: {}",
        redisService.isInitInProgress(), redisService.isInitProcessedViaSftp());
      return true;
    }

    // * check for empty UID
    if(StringUtils.isEmpty(reqQueryExceptionDto.getUid())) {
      log.error("Unable to process query exception list, Empty UID");
      return true;
    }

    if (StringUtils.isEmpty(lastUpdatedTime)) {
      log.error("Unable to hit netc, last updated time not found for msg id {}", reqQueryExceptionDto.getUid());
      return true;
    }

    if(!reqQueryExceptionDto.isLastDiffFromSftp() && retryCount == 0) {
      lastUpdatedTime = Utils.getFormattedDate(Utils.getLocalDateTime(lastUpdatedTime));
    }
    long secDiff = Utils.getTimeDiff(lastUpdatedTime);
    if (secDiff > SEC_IN_DAY) {
      log.warn("Didn't found any successful DIFF/INIT in last 24 hours. Not running DIFF for msg id {}.",
        reqQueryExceptionDto.getUid());
      return true;
    }

    reqQueryExceptionDto.setLastUpdatedTime(lastUpdatedTime);
    return false;
  }

  public void validateSftpFiles(Map<String, String> fileMap, String folderName) {
    byte[] contentByte;
    StringBuilder sb = new StringBuilder();
    sb.append(folderName).append(File.separator)
      .append(LocalDate.now());
    SFTPUtil.createLocalDirStructure(sb.toString());
    for (Map.Entry<String, String> entry : fileMap.entrySet()) {
      try {
        contentByte = fileService.readFile(entry.getValue());

        createDecryptedCsvFile(contentByte, entry.getKey(), sb.toString());
      }
      catch (Exception e) {
        log.error("Error occurred while validating the sftp files {}", e, entry.getValue());
        throw new NetcEngineException(ErrorMessage.FILE_VALIDATION_ERROR);
      }
    }
  }

  private void createDecryptedCsvFile(byte[] contentByte, String filename, String path) {
    FileChannel channel = null;

    String content = new String(contentByte);
    ExceptionFileXml exceptionFileXml = XmlUtil.deserializeXmlDocument(content, ExceptionFileXml.class);
    byte[] csvData = Base64.getDecoder().decode(exceptionFileXml.getOrgContent().replace("\n", ""));
    try(RandomAccessFile stream = new RandomAccessFile(path + File.separator + filename, "rw")) {

      channel = stream.getChannel();
      ByteBuffer buffer = ByteBuffer.allocate(csvData.length);
      buffer.put(csvData);
      buffer.flip();
      channel.write(buffer);
    }
    catch (IOException e) {
      log.error("[createDecryptedCsvFile] File not found exception occurred for {} at path {} ", e, filename, path);
      //throw new NetcEngineException("File not found exception occurred")
    }
    finally {
      if (channel != null) {
        try {
          channel.close();
        } catch (IOException e) {
          log.warn("[createDecryptedCsvFile] unable to close the opened channel", e);
        }
      }
    }

  }

  public Map<String, String> validateDiffFilesOrdering(Map<String, String> fileMap,
    DiffFileNameDto lastDiffFileNameDto) {
    Map<LocalDateTime, LocalDateTime> fileFromToDateTimeMap = new HashMap<>();
    Map<LocalDateTime, Set<Integer>> fileFromDateSequenceMap = new HashMap<>();
    if(Objects.nonNull(lastDiffFileNameDto)) {
      int lastSequence = Integer.parseInt(lastDiffFileNameDto.getSequence());
      fileFromToDateTimeMap.put(lastDiffFileNameDto.getFromDatetime(), lastDiffFileNameDto.getToDatetime());
      fileFromDateSequenceMap.put(lastDiffFileNameDto.getFromDatetime(), new HashSet<>());

      int sequence = 1;
      while(sequence <= lastSequence) {
        fileFromDateSequenceMap.get(lastDiffFileNameDto.getFromDatetime()).add(sequence);
        sequence++;
      }
    }

    for (Map.Entry<String, String> entry : fileMap.entrySet()) {

      DiffFileNameDto fileNameDto = commonService.transformDiffFileNameToDto(entry.getKey());

      Integer sequence = Integer.parseInt(fileNameDto.getSequence());
      LocalDateTime endDateTime = fileFromToDateTimeMap.get(fileNameDto.getFromDatetime());
      if(Objects.nonNull(endDateTime)) {
        if(!endDateTime.isEqual(fileNameDto.getToDatetime())) {
          log.error("File with same start time and different end time {}", fileMap.keySet());
          throw new NetcEngineException(ErrorMessage.INVALID_FILE_FORMAT);
        }
      }
      else {
        fileFromToDateTimeMap.put(fileNameDto.getFromDatetime(), fileNameDto.getToDatetime());
        fileFromDateSequenceMap.put(fileNameDto.getFromDatetime(), new HashSet<>());
      }
      fileFromDateSequenceMap.get(fileNameDto.getFromDatetime()).add(sequence);
    }

    return checkIfDiffFileIsContiguousByTime(fileFromToDateTimeMap, fileFromDateSequenceMap, fileMap);
  }

  private Map<String, String> checkIfDiffFileIsContiguousByTime(Map<LocalDateTime, LocalDateTime> fileFromToDateTimeMap,
    Map<LocalDateTime, Set<Integer>> fileFromDateSequenceMap, Map<String, String> fileMap) {

    Map<String, String> sortedFileMap = new LinkedHashMap<>();
    LocalDateTime startTime = Collections.min(fileFromToDateTimeMap.keySet());
    int totalFiles = fileFromToDateTimeMap.size();
    while(totalFiles != 0) {
      LocalDateTime endTime = fileFromToDateTimeMap.get(startTime);
      if(Objects.isNull(endTime)) {
        log.error("Files are not contiguous for time {} of map {}", startTime, fileFromToDateTimeMap);
        throw new NetcEngineException(ErrorMessage.INVALID_FILE_FORMAT);
      }

      int sequence = 1;
      Set<Integer> sequenceSet = fileFromDateSequenceMap.get(startTime);
      while (sequence <= sequenceSet.size()) {
        if(!sequenceSet.contains(sequence)) {
          log.error("File with start time {} has a missing sequence number {}", startTime, sequence);
          throw new NetcEngineException(ErrorMessage.INVALID_FILE_FORMAT);
        }

        String fromTime = startTime.format(DateTimeFormatter.ofPattern(Constants.NETC_DIFF_DATE_TIME_FORMAT));
        String toTime = endTime.format(DateTimeFormatter.ofPattern(Constants.NETC_DIFF_DATE_TIME_FORMAT));
        String fileName = sftpConfig.getDiffFileName() + fromTime + Constants.HYPEN_STR + toTime +
          Constants.HYPEN_STR + getDiffFileSequenceWithZeros(sequence) + Constants.CSV_EXT;

        // * to eliminate lastDiffFileNameDto files
        if(Objects.nonNull(fileMap.get(fileName))) {
          sortedFileMap.put(fileName, fileMap.get(fileName));
        }
        sequence++;
      }

      startTime = endTime.plusSeconds(1);
      totalFiles--;
    }

    return sortedFileMap;
  }

  private String getDiffFileSequenceWithZeros(int sequence) {
    String appendZeros = "";
    if(sequence < 99) {
      appendZeros += "0";
    }
    if(sequence < 9) {
      appendZeros += "0";
    }
    return appendZeros + sequence;
  }

  public void validateDiffHeaders(Map<String, String> fileMap) {
    for (Map.Entry<String, String> entry : fileMap.entrySet()) {
      String fullName = sftpConfig.getDecryptedDiffFolder() + File.separator +
        LocalDate.now() + File.separator + entry.getKey();
      try (BufferedReader reader = new BufferedReader(new FileReader(fullName));
           Stream<String> stream = Files.lines(Paths.get(fullName))) {
        String headers = reader.readLine();
        ExceptionDiffHeaderDto exceptionHeaderDto =
          SFTPUtil.convertToObject(headers, ExceptionDiffHeaderDto.class, ",");

        long count = stream.skip(2L).count();
        log.info("Total records {} for file {} ", count, entry.getKey());

        if (!validateCsvData(exceptionHeaderDto, count, entry.getKey())) {
          log.error("Header validation error for the diff file {} with record {} and to date as {} and from date as {}",
            entry.getKey(), exceptionHeaderDto.getFileCount(),
            exceptionHeaderDto.getFileFromDatetime(), exceptionHeaderDto.getFileToDatetime());
          throw new NetcEngineException(ErrorMessage.INVALID_FILE_HEADERS);
        }
      }
      catch (IOException ex) {
        log.error("[Validate Headers], Error {} occurred while reading header for file {}", ex, fullName);
        throw new NetcEngineException(ErrorMessage.FILE_NOT_FOUND);
      }
    }
  }

  private boolean validateCsvData(ExceptionDiffHeaderDto exceptionHeaderDto, long count, String filename) {
    LocalDateTime fileFromDateTime =
      LocalDateTime.parse(exceptionHeaderDto.getFileFromDatetime(), DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));
    LocalDateTime fileToDateTime =
      LocalDateTime.parse(exceptionHeaderDto.getFileToDatetime(), DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"));

    DiffFileNameDto fileNameDto = commonService.transformDiffFileNameToDto(filename);

    if(!fileNameDto.getFromDatetime().isEqual(fileFromDateTime)) {
      log.error("Header validation error for the diff file {} with invalid start time", filename);
      return false;
    }
    else if (!fileNameDto.getToDatetime().isEqual(fileToDateTime)) {
      log.error("Header validation error for the diff file {} with invalid end time", filename);
      return false;
    }
    else if (!fileNameDto.getSequence().equals(exceptionHeaderDto.getFileCount())) {
      log.error("Header validation error for the diff file {} with invalid file count", filename);
      return false;
    }
    else {
      return Long.parseLong(exceptionHeaderDto.getFileRecords()) == count;
    }

  }

  public void validateListParticipantResponse(RespListParticipantXml respListParticipantXml) {
    if(respListParticipantXml.getTransaction().getResp().getParticipantList().isEmpty()) {
      throw new NetcEngineException("Empty List found in NPCI participant response");
    }
    
    Set<String> issuerIinSet = new HashSet<>();
    Set<String> acquirerIinSet = new HashSet<>();
    
    for(RespListParticipantXml.RespListParticipantRespXml.ParticipantXml participantXml: respListParticipantXml.getTransaction().getResp().getParticipantList()) {
      if (!participantXml.getIssuerIin().isBlank() && !issuerIinSet.add(participantXml.getIssuerIin())) {
        throw new NetcEngineException("Duplicate issuerIin found");
      }
      if (!participantXml.getAcquirerIin().isBlank() && !acquirerIinSet.add(participantXml.getAcquirerIin())) {
        throw new NetcEngineException("Duplicate acquirerIin found");
      }
    }
    
  }
}
