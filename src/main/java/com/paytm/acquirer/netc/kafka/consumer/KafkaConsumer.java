package com.paytm.acquirer.netc.kafka.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytm.acquirer.netc.dto.cron.CronResponse;
import com.paytm.acquirer.netc.dto.getException.RespGetExceptionListXml;
import com.paytm.acquirer.netc.dto.kafka.ExceptionMetaInfo;
import com.paytm.acquirer.netc.dto.pay.ReqPay;
import com.paytm.acquirer.netc.dto.queryException.ReqQueryExceptionDto;
import com.paytm.acquirer.netc.dto.retry.RetryDto;
import com.paytm.acquirer.netc.enums.RetrialType;
import com.paytm.acquirer.netc.service.AsyncTxnService;
import com.paytm.acquirer.netc.service.CustomMetricService;
import com.paytm.acquirer.netc.service.InitService;
import com.paytm.acquirer.netc.service.ReqPayService;
import com.paytm.acquirer.netc.service.TagExceptionService;
import com.paytm.acquirer.netc.service.common.RedisService;
import com.paytm.acquirer.netc.service.retry.CronRetryService;
import com.paytm.acquirer.netc.service.retry.RetryResponseService;
import com.paytm.acquirer.netc.util.Constants;
import com.paytm.acquirer.netc.util.XmlUtil;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import com.paytm.transport.metrics.Monitor;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import java.util.Map;

import static com.paytm.acquirer.netc.util.Constants.RetryParams.*;

@Service
@RequiredArgsConstructor
public class KafkaConsumer {
  private static final Logger log = LoggerFactory.getLogger(KafkaConsumer.class);

  private final ReqPayService reqPayService;
  private final ObjectMapper mapper;
  private final KafkaListenerEndpointRegistry registry;
  private final CircuitBreaker circuitBreaker;
  private final CronRetryService retryService;
  private final TagExceptionService exceptionService;
  private final AsyncTxnService asyncTxnService;
  private final RedisService redisService;
  private final InitService initService;
  private final CustomMetricService customMetricService;
  private final RetryResponseService retryResponseService;

  @Value("${kafka.consumer-group.req-pay}")
  private String reqPayGroup;

  @KafkaListener(topics = "${kafka.topic.reqPay}", groupId = "${kafka.consumer-group.req-pay}",
      concurrency = "${kafka.workers.reqPay}")
  public void receiveReqPay(@Payload String stringData, final Acknowledgment ack) {
    log.info("received data from reqPay queue ='{}'", stringData);

    try {
      ReqPay reqPay = mapper.readValue(stringData, ReqPay.class);
      reqPayService.requestPayment(reqPay);
    } catch (Exception e) {
      log.error("problem while consuming from kafka", e);
    } finally {
      ack.acknowledge();
    }
  }
  
  
  @KafkaListener(topics = "${kafka.topic.init}", groupId = "${kafka.consumer-group.init}",
    concurrency = "${kafka.workers.init}")
  public void receiveInitCallBack(@Payload String stringData, final Acknowledgment ack) {
    RespGetExceptionListXml requestExceptionList = new RespGetExceptionListXml();
    try {
      requestExceptionList = XmlUtil.deserializeXmlDocument(stringData, RespGetExceptionListXml.class);
      log.info("received data from init queue for msgId {}, msgNumber {}", requestExceptionList.getHeader().getMessageId(), requestExceptionList.getTransaction().getResponse().getMessageNumber());
      retryResponseService.handleInitCallBackData(requestExceptionList);
    } catch (Exception e) {
      log.error("problem while handling callback", e);
    } finally {
      ack.acknowledge();
    }
  }
  
  @KafkaListener(topics = "${netc.retry-cron.topic-consume}", groupId = "${kafka.consumer-group.retry}",
      concurrency = "${kafka.workers.retry}")
  public void processRetryFlow(@Payload String stringData, final Acknowledgment ack) {
    log.info("received data from retry queue = {}", stringData);

    try {
      CronResponse<RetryDto> cronResponse = mapper.readValue(stringData, new TypeReference<CronResponse<RetryDto>>() {
      });
      RetryDto data = cronResponse.getMessageBody();
      switch (data.getApi()) {
        case RESP_PAY:
          if (RetrialType.CIRCUIT_BREAKER_RETRY.equals(data.getRetrialType())) {
            reqPayService.handleCircuitBreakerReqPay(data);
            return;
          } else if(RetrialType.AUTO_RETRYABLE_CODE_RETRY.equals(data.getRetrialType())) {
            reqPayService.handleAutoRetryableCode(data);
            return;
          }
          retryService.respPayRetry(data);
          break;
        case RESP_QUERY_EXCEPTION_LIST:
        case RESP_GET_EXCEPTION_LIST:
          retryService.exceptionListRetry(data, data.getApi());
          break;
        case GET_EXCEPTION_LIST:
          int count = Integer.parseInt(data.getParams().get(RETRY_COUNT));
          ExceptionMetaInfo additionalParam =
            asyncTxnService.getAdditionalParamByMsgId(
              data.getMsgId(),
              ExceptionMetaInfo.class);
          initService.getExceptionList(count, additionalParam);
          break;
        case QUERY_EXCEPTION_LIST:
          int retryCount = Integer.parseInt(data.getParams().get(RETRY_COUNT));
          String uid = data.getParams().getOrDefault(Constants.UID,"");
          String lastUpdatedTime = data.getParams().getOrDefault(Constants.LAST_UPDATED_TIME,"");
          ReqQueryExceptionDto reqQueryExceptionDto = new ReqQueryExceptionDto();
          reqQueryExceptionDto.setUid(uid);
          reqQueryExceptionDto.setLastUpdatedTime(lastUpdatedTime);
          exceptionService.queryExceptionList(retryCount, reqQueryExceptionDto);
          break;
        default:
          log.error("API {} is not handled here. Please check", data.getApi());
      }
    } catch (Exception e) {
      log.error("Could not deserialize consumed message into {} class.", e, CronResponse.class);
    } finally {
      ack.acknowledge();
    }
  }

  @KafkaListener(topics = "${kafka.topic.fileUploadedEvent}", groupId = "netc-engine-mark-fetch-exception-success",
      concurrency = "${kafka.workers.fileUploadedEvent}")
  public void processFileUploadedEvent(@Payload String data, Acknowledgment ack) {
    try {
      Map<String, ?> event = mapper.readValue(data, new TypeReference<Map<String, ?>>() {});
      String msgId = event.get("messageId").toString();
      Boolean isSuccess = !event.containsKey("isSuccess") || BooleanUtils.toBoolean(event.get("isSuccess").toString());
      log.info("ExceptionHandler Completed Rxed Msg:{}, isSuccess: {}", msgId, isSuccess);
      Assert.notNull(msgId, "messageId must not be null in fileUpload event");
      asyncTxnService.markFetchExceptionSuccessful(msgId, isSuccess);
    } catch (Exception e) {
      log.error("Unable to process fileUpload event with data {}", e, data);
    } finally {
      ack.acknowledge();
    }
  }

  @PostConstruct
  private void init() {
    //setting up event listener for circuit breaker
    circuitBreaker.getEventPublisher().onStateTransition(event -> {
      CircuitBreaker.StateTransition transition = event.getStateTransition();
      log.info("circuit breaker transitioned from {} -> {}", transition.getFromState(), transition.getToState());
      customMetricService.recordMetricForCircuitBreaker(Monitor.ServiceGroup.COUNTER, transition.toString());
      
      switch (transition) {
        case HALF_OPEN_TO_OPEN:
        case CLOSED_TO_OPEN:
          //stop consuming from kafka
          pauseConsumers();
          break;
        case HALF_OPEN_TO_CLOSED:
        case OPEN_TO_CLOSED:
          //start consuming from kafka
          resumeConsumers();
          break;
        default:
          break;
      }
    });
  }

  private void pauseConsumers() {
    log.info("stopping consumers for netc_req_pay");
    log.info("setting key in redis");
    redisService.setCircuitOpenKey();
    log.info("key in redis is set");
    registry.getListenerContainer(reqPayGroup).stop();
  }

  private void resumeConsumers() {
    log.info("resuming consumers for netc_req_pay");
    log.info("removing key from redis");
    redisService.removeCircuitOpenKey();
    log.info("key from redis is removed");
    registry.getListenerContainer(reqPayGroup).start();
  }

}
