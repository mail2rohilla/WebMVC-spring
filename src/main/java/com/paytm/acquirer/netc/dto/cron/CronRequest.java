package com.paytm.acquirer.netc.dto.cron;

import lombok.Data;

@Data
public class CronRequest<T> {
    private String clientOperationId;
    private T messageBody;
    private Integer nextRetryInSeconds;
}
