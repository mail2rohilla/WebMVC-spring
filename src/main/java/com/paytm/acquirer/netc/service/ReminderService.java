package com.paytm.acquirer.netc.service;

import com.paytm.transport.JsonUtil;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import com.paytm.transport.model.ReminderMessage;
import com.paytm.transport.service.AcquirerReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReminderService {

  private final AcquirerReminderService acquirerReminderService;

  @Value("${netc.retry-cron.topic-produce}")
  private String reminderProducerTopic;

  @Value("${netc.retry-cron.client-id}")
  private String clientId;

  private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

  public void addReminder(Object netcReq, String msgId, Long timeout) {
   ReminderMessage message = ReminderMessage.builder().
      clientOperationId(clientId)
      .messageBody(netcReq)
      .nextRetryInSeconds(timeout)
      .messageId(msgId)
      .build();

    log.info(
      "Creating new reminder with topic: {} with data:{} and timeout:{} to reminder system ",
      reminderProducerTopic,
      JsonUtil.serialiseJson(message),
      timeout);
    try {
      acquirerReminderService.addReminder(message, reminderProducerTopic);
    } catch (Exception exception) {
      log.error("Error creating reminder with topic: {} with data:{}",exception,
        reminderProducerTopic, JsonUtil.serialiseJson(message));
    }
  }

  @Async("exception-list-executor")
  public void deleteReminder(String msgId){
    try {
      acquirerReminderService.deleteReminder(msgId);
      log.info("Reminder event deleted from reminder service with msg id:{}", msgId);
    } catch (Exception exception) {
      log.error("Error deleting reminder with data:{}", exception);
    }
  }
}

