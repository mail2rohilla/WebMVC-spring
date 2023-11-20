package com.paytm.acquirer.netc.kafka.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paytm.acquirer.netc.dto.cron.CronRequest;
import com.paytm.acquirer.netc.dto.getException.RespGetExceptionListXml;
import com.paytm.acquirer.netc.dto.kafka.ExceptionMessage;
import com.paytm.acquirer.netc.dto.kafka.QueryExceptionMessage;
import com.paytm.acquirer.netc.dto.pay.ReqPay;
import com.paytm.acquirer.netc.dto.pay.RespPay;
import com.paytm.acquirer.netc.dto.retry.ReqPayRetry;
import com.paytm.acquirer.netc.util.Constants;
import com.paytm.acquirer.netc.util.JsonUtil;
import com.paytm.acquirer.netc.util.XmlUtil;
import com.paytm.transport.kafka.TransportKafkaTemplate;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Service
@RequiredArgsConstructor
public class KafkaProducer {
  private static final Logger log = LoggerFactory.getLogger(KafkaProducer.class);

  private final TransportKafkaTemplate<Object> kafkaTemplate;
  private final ObjectMapper objectMapper;
  
  @Qualifier("compressedKafkaTemplate")
  @Autowired
  private KafkaTemplate<String, Object> compressedKafkaTemplate;
  
  private TransportKafkaTemplate<Object> compressedTransportKafkaTemplate;
  
  @PostConstruct
  public void init() {
    compressedTransportKafkaTemplate =
      new TransportKafkaTemplate<>(compressedKafkaTemplate, compressedKafkaTemplate);
  }
  
  @Value("${kafka.topic.respPay}")
  private String respPayTopic;

  @Value("${kafka.topic.reqPay}")
  private String reqPayTopic;

  @Value("${kafka.topic.reqPayRetry}")
  private String reqPayRetryTopic;

  @Value("${kafka.topic.exceptionList}")
  private String exceptionListTopic;

  @Value("${kafka.topic.fetch-exception-data}")
  private String fetchExceptionDataTopic;

  @Value("${kafka.topic.query-exception-list-resp}")
  private String queryExceptionData;

  @Value("${netc.retry-cron.client-id}")
  private String cronClientId;

  @Value("${netc.retry-cron.topic-produce}")
  private String cronProducerTopic;
  
  @Value("${kafka.topic.init}")
  private String initNetcCallback;
  
  public void send(RespPay data) {
    log.info("sending to topic='{}' and data='{}'", respPayTopic, JsonUtil.serialiseJson(data));
    SendResult<String, ?> sr = kafkaTemplate.sendSync(respPayTopic, data.getRefId(), data);
    log.info("Record sent with key {} to topic {} with meta : {} (partition@offset)", data.getRefId(),
        respPayTopic, sr.getRecordMetadata());
  }

  public void sendExceptionListKeys(ExceptionMessage message) {
    log.info("Sending INITExceptionKeys to ExceptionHandler msg:{} Topic:{}"
            , JsonUtil.serialiseJson(message), exceptionListTopic);
    kafkaTemplate.sendSync(exceptionListTopic, null, 0, message);
  }
  
  public void sendInitCompressedData(RespGetExceptionListXml message) {
    log.info("Sending data for INIT msgId:{} Topic:{}"
      , message.getHeader().getMessageId(), initNetcCallback);
    compressedTransportKafkaTemplate.sendSync(initNetcCallback, null, 0, XmlUtil.serializeXmlDocument(message));
  }
  
  public void publishQueryExceptionData(QueryExceptionMessage message) {
    log.info("Sending DIFFExceptionKeys to ExceptionHandler msg:{} Topic:{} ",
            JsonUtil.serialiseJson(message), exceptionListTopic);
    message.setType(Constants.KafkaConstants.DIFF);
    kafkaTemplate.sendSync(exceptionListTopic, null, 0, message);
  }

  public void sendReqPayRetryMsg(ReqPay data) {
    log.info("sending to topic='{}' and data='{}' ", reqPayTopic, JsonUtil.serialiseJson(data));
    kafkaTemplate.sendSync(reqPayTopic, data);
  }

  public <T> void scheduleRetry(T data, Integer timeout) {
    log.info(
        "sending to topic {} with data:{} and timeout:{} to retry system ",
        cronProducerTopic,
        JsonUtil.serialiseJson(data),
        timeout);

    CronRequest<T> retryRequest = new CronRequest<>();
    retryRequest.setClientOperationId(cronClientId);
    retryRequest.setMessageBody(data);
    retryRequest.setNextRetryInSeconds(timeout);

    kafkaTemplate.sendSync(cronProducerTopic, retryRequest);
  }

  public void notifyTxnManagerToRetryReqPay(ReqPayRetry reqPayRetry) {
    log.info(
        "sending to topic {} and data: {} to txn manager for reqPay retry",
        reqPayRetryTopic,
        JsonUtil.serialiseJson(reqPayRetry));

    kafkaTemplate.sendSync(reqPayRetryTopic, reqPayRetry);
  }
}
