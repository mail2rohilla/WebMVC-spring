package com.paytm.acquirer.netc.config;

import com.paytm.acquirer.netc.config.properties.CircuitBreakerProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;

@Configuration
public class CircuitBreakerConfiguration {

  @Bean
  public CircuitBreakerRegistry circuitBreakerRegistry(CircuitBreakerProperties properties) {
    return CircuitBreakerRegistry.of(circuitBreakerConfig(properties));
  }

  @Bean
  public CircuitBreaker circuitBreaker(CircuitBreakerRegistry registry) {
    return registry.circuitBreaker("circuit_breaker");
  }

  private CircuitBreakerConfig circuitBreakerConfig(CircuitBreakerProperties properties) {
    return CircuitBreakerConfig.custom()
        .failureRateThreshold(properties.getFailureThreshold())         // 25% of request failed then circuit will be opened
        .ringBufferSizeInClosedState(properties.getRingBufferClosed())   // if 3 request failed out of 10 then circuit will break
        .ringBufferSizeInHalfOpenState(properties.getRingBufferHalfOpen()) // if 1 of 4 request failed then circuit will remain open else will be closed.
        .waitDurationInOpenState(Duration.ofMillis(properties.getOpenStateWaitDurationMs()))
        .recordExceptions(ResourceAccessException.class, HttpClientErrorException.class, HttpStatusCodeException.class)
        .build();
  }
}
