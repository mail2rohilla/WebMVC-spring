package com.paytm.acquirer.netc.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "netc.circuit-breaker")
public class CircuitBreakerProperties {
    private Integer failureThreshold;
    private Integer ringBufferClosed;
    private Integer ringBufferHalfOpen;
    private Integer openStateWaitDurationMs;
}
