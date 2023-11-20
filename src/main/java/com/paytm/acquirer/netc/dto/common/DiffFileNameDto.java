package com.paytm.acquirer.netc.dto.common;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DiffFileNameDto {
  private LocalDateTime fromDatetime;

  private LocalDateTime toDatetime;

  private String sequence;
}
