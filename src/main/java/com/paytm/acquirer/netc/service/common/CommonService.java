package com.paytm.acquirer.netc.service.common;

import com.paytm.acquirer.netc.dto.common.DiffFileNameDto;
import com.paytm.acquirer.netc.dto.getException.SFTPConfig;
import com.paytm.acquirer.netc.enums.ErrorMessage;
import com.paytm.acquirer.netc.exception.NetcEngineException;
import com.paytm.acquirer.netc.util.Constants;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CommonService {
  private static final Logger log = LoggerFactory.getLogger(CommonService.class);

  private final SFTPConfig sftpConfig;

  public DiffFileNameDto transformDiffFileNameToDto(String filename) {
    if(Objects.isNull(filename)) {
      return null;
    }

    String fileNameWithoutExt = FilenameUtils.removeExtension(filename);
    fileNameWithoutExt = fileNameWithoutExt.replace(sftpConfig.getDiffFileName(), "");
    List<String> fileNameData = Arrays.asList(fileNameWithoutExt.split("-"));
    if(fileNameData.size() != Constants.NETC_DIFF_FILE_LENGTH_AFTER_EXT) {
      log.error("Invalid file name {}", filename);
      throw new NetcEngineException(ErrorMessage.INVALID_FILE_FORMAT);
    }

    LocalDateTime fromDateTime =
      LocalDateTime.parse(fileNameData.get(0), DateTimeFormatter.ofPattern(Constants.NETC_DIFF_DATE_TIME_FORMAT));
    LocalDateTime toDateTime =
      LocalDateTime.parse(fileNameData.get(1), DateTimeFormatter.ofPattern(Constants.NETC_DIFF_DATE_TIME_FORMAT));

    if(fromDateTime.isAfter(toDateTime)) {
      log.error("Invalid file when from time greater than to time {}", filename);
      throw new NetcEngineException(ErrorMessage.INVALID_FILE_FORMAT);
    }

    if(!Constants.NETC_DIFF_FILE_SEQUENCE_LENGTH.equals(fileNameData.get(2).length())) {
      log.error("Invalid file sequence format {}", filename);
      throw new NetcEngineException(ErrorMessage.INVALID_FILE_FORMAT);
    }

    DiffFileNameDto diffFileNameDto = new DiffFileNameDto();
    diffFileNameDto.setFromDatetime(fromDateTime);
    diffFileNameDto.setToDatetime(toDateTime);
    diffFileNameDto.setSequence(fileNameData.get(2));
    return diffFileNameDto;
  }
}
