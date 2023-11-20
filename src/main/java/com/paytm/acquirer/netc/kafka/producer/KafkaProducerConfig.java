package com.paytm.acquirer.netc.kafka.producer;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.DefaultKafkaHeaderMapper;
import org.springframework.kafka.support.converter.MessagingMessageConverter;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

  @Value("${spring.kafka.bootstrap-servers}")
  private String bootstrapServers;

  private <T> ProducerFactory<String, T> producerFactory() {
    return new DefaultKafkaProducerFactory<>(producerConfigs());
  }

  @Primary
  @Bean(name = "KafkaTemplate")
  public KafkaTemplate<String, Object> KafkaTemplate() {
    KafkaTemplate kafkaTemplate = new KafkaTemplate<>(producerFactory());
    kafkaTemplate.setMessageConverter(converter());
    return kafkaTemplate;
  }

  private Map<String, Object> producerConfigs() {
    Map<String, Object> props = new HashMap<>();
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    return props;
  }
  
  public <T> ProducerFactory<String, T> compressedProducerFactory() {
    return new DefaultKafkaProducerFactory<>(compressedProducerConfigs());
  }
  
  @Bean(name = "compressedKafkaTemplate")
  public <T> KafkaTemplate<String, T> compressedKafkaTemplate() {
    KafkaTemplate compressedKafkaTemplate = new KafkaTemplate<>(compressedProducerFactory());
    compressedKafkaTemplate.setMessageConverter(converter());
    return compressedKafkaTemplate;
  }

  private Map<String, Object> compressedProducerConfigs() {
    Map<String, Object> props = new HashMap<>();
    
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
    props.put(ProducerConfig.ACKS_CONFIG, "all");
    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
    props.put(ProducerConfig.LINGER_MS_CONFIG, 5);
    props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
    props.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, "4194304");
  
    return props;
  }

  private MessagingMessageConverter converter() {
    MessagingMessageConverter converter = new MessagingMessageConverter();
    DefaultKafkaHeaderMapper mapper = new DefaultKafkaHeaderMapper();
    mapper.setEncodeStrings(true);
    converter.setHeaderMapper(mapper);
    return converter;
  }
}
