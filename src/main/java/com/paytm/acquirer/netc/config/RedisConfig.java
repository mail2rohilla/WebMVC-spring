package com.paytm.acquirer.netc.config;

import com.paytm.acquirer.netc.db.entities.IinInfo;
import com.paytm.acquirer.netc.dto.pay.ReqPay;
import com.paytm.acquirer.netc.dto.queryException.RedisTag;
import com.paytm.acquirer.netc.service.StorageServiceWrapper;
import com.paytm.acquirer.netc.service.common.RedisService;
import com.paytm.acquirer.netc.util.Constants;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import com.paytm.transport.metrics.DataDogClient;
import com.paytm.transport.metrics.Monitor;
import com.paytm.transport.redis.CommonRedisConnectionFactory;
import com.paytm.transport.redis.CommonRedisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.listener.RetryListenerSupport;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Configuration
@RequiredArgsConstructor
public class RedisConfig {

    @Value("${redis.connect-timeout}")
    private Integer redisConnectTimeout;

    @Value("${redis.read-timeout}")
    private Integer redisReadTimeout;

    private final CommonRedisConnectionFactory commonRedisConnectionFactory;

    @Bean
    RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        final RedisTemplate<String, Object> template = new RedisTemplate<>();

        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(Object.class));

        return template;
    }

    @Bean
    RedisTemplate<String, RedisTag> redisTemplateForTag(RedisConnectionFactory connectionFactory) {
        final RedisTemplate<String, RedisTag> template = new RedisTemplate<>();

        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(RedisTag.class));

        return template;
    }
    
    @Bean
    RedisTemplate<String, ReqPay> redisTemplateForReqPay(RedisConnectionFactory connectionFactory) {
        final RedisTemplate<String, ReqPay> template = new RedisTemplate<>();

        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(ReqPay.class));

        return template;
    }
    
    @Primary
    @Bean
    RedisTemplate<String, Integer> redisTagExceptionCodeTemplate(RedisConnectionFactory connectionFactory) {
        final RedisTemplate<String, Integer> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(Integer.class));
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new Jackson2JsonRedisSerializer<>(Integer.class));
        return template;
    }
    
    @Bean
    @Primary
    RedisConnectionFactory connectionFactory() {
        return commonRedisConnectionFactory.getRedisConnectionFactory(
          CommonRedisProperties.builder()
            .redisReadTimeout(redisReadTimeout)
            .redisConnectTimeout(redisConnectTimeout)
            .build());
    }

    @Bean
    RedisTemplate<String, IinInfo> redisTemplateForIin(RedisConnectionFactory connectionFactory) {
        final RedisTemplate<String, IinInfo> template = new RedisTemplate<>();
        
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(IinInfo.class));
        
        return template;
    }
    
    @Component
    @RequiredArgsConstructor
    public static class RedisListenerSupport extends RetryListenerSupport {
        private final Logger logger = LoggerFactory.getLogger(RedisListenerSupport.class);
        private final DataDogClient dataDogClient;

        @Override
        public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
            logger.error("Method {} failed {}th time. Error: ", throwable, context.getAttribute(RetryContext.NAME),
                context.getRetryCount());
            if (Objects.toString(context.getAttribute(RetryContext.NAME)).contains(RedisService.class.getSimpleName())) {
                dataDogClient.recordExecutionMetric(Monitor.ServiceGroup.REDIS.name(), System.currentTimeMillis(),
                    Objects.toString(context.getAttribute(RetryContext.NAME)), Constants.FAILURE_METRIC);
            } else if (Objects.toString(context.getAttribute(RetryContext.NAME)).contains(StorageServiceWrapper.class.getSimpleName())) {
                dataDogClient.recordExecutionMetric(Monitor.ServiceGroup.S3.name(), System.currentTimeMillis(),
                    Objects.toString(context.getAttribute(RetryContext.NAME)), Constants.FAILURE_METRIC);
            }
        }
    }
}
