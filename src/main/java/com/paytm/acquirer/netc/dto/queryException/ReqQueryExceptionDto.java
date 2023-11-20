package com.paytm.acquirer.netc.dto.queryException;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class ReqQueryExceptionDto {
  @NotNull
  @Schema(description = "UID")
  private String uid;

  @NotNull
  @Schema(description = "Last Updated Time")
  private String lastUpdatedTime;

  @Schema(description = "Force Use Sftp For Diff")
  private Boolean forceUseSftpForDiff;

  @Schema(description = "Last Diff From Sftp")
  private boolean lastDiffFromSftp = false;

  @Schema(description = "Last Sftp File Name")
  private String lastSftpFileName;

  @Schema(description = "Test Request Via API")
  private boolean testRequestViaAPI = false;

}
