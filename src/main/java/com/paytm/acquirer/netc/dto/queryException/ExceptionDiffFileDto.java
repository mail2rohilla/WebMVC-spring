package com.paytm.acquirer.netc.dto.queryException;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.sql.Timestamp;

@Data
@RequiredArgsConstructor
public class ExceptionDiffFileDto {

  private String tagId;

  private String exceptionCode;

  private String operation;

  private Timestamp updatedTime;


}