package com.paytm.acquirer.netc.dto.getException;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;

@Getter
@Setter
@RequiredArgsConstructor
public class ExceptionFileDto {

  private String tagId;

  private String exceptionCode;

  private Timestamp updatedTime;


}
