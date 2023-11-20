package com.paytm.acquirer.netc.dto.common;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionHeaderDto {

  private String fileCreationDate;

  private String fileRecords;

  private String fileCount;

}
