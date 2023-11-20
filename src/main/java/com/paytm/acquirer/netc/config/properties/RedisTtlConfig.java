package com.paytm.acquirer.netc.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "redis.ttl")
public class RedisTtlConfig {
    private Integer initDataTtl;
    private Integer diffDataTtl;
    private Integer msgIdTtl;
    private Integer reqPayDataTtl;
    private Integer initInProgressTtl;
    private Integer initCompletionTtl;
    private Integer sftpProcessedTtl;
    private Integer circuitBreakerCounterTtl;
    private Integer iinParticipantListTtl;
}
