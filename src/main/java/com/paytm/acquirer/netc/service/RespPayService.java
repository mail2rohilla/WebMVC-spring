package com.paytm.acquirer.netc.service;

import com.paytm.acquirer.netc.dto.pay.RespPay;
import com.paytm.acquirer.netc.kafka.producer.KafkaProducer;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class RespPayService {

  private KafkaProducer producer;

  public void handleData(RespPay respPay) {
    producer.send(respPay);
  }
}
