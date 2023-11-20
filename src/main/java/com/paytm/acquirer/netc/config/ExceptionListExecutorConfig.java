package com.paytm.acquirer.netc.config;

import com.paytm.transport.service.ThreadPoolMetricService;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;

@EnableAsync
@Configuration
@RequiredArgsConstructor
public class ExceptionListExecutorConfig implements AsyncConfigurer {
    private  final ThreadPoolMetricService threadPoolMetricService;

    @Override
    @Bean("exception-list-executor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setTaskDecorator(
          runnable -> {
              Map<String, String> contextMap = MDC.getCopyOfContextMap();
              //add threadpool details
              threadPoolMetricService.setThreadPoolDetails(executor,contextMap);
              return () -> {
                  try {
                      MDC.setContextMap(contextMap);
                      runnable.run();
                  } finally {
                      MDC.clear();
                  }
              };
          });
        executor.setCorePoolSize(100);
        executor.setMaxPoolSize(100);
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }
}