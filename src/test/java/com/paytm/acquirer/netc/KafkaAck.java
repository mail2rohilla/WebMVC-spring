package com.paytm.acquirer.netc;

import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import org.springframework.kafka.support.Acknowledgment;

public class KafkaAck implements Acknowledgment {
  
  private static final Logger log = LoggerFactory.getLogger(KafkaAck.class);
  
  @Override
  public void acknowledge() {
    log.info("[Ack]");
  }
}
