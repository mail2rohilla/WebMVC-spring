package com.paytm.acquirer.netc.dto.cron;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CronResponse<T> {
  private String clientOperationId;
  private T messageBody;
  private String messageId;
  private String errorMessage;
}
