package com.paytm.acquirer.netc.dto.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionDiffHeaderDto {

  private String fileFromDatetime;

  private String fileToDatetime;

  private String fileRecords;

  private String fileCount;

}