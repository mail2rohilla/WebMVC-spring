package com.paytm.acquirer.netc;

import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.logging.log4j.core.lookup.MainMapLookup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.retry.annotation.EnableRetry;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;

@EnableRetry
@SpringBootApplication(scanBasePackages = "com.paytm")
@EnableAutoConfiguration(exclude = KafkaAutoConfiguration.class)
public class Application {
  private static final Logger logger = LoggerFactory.getLogger(Application.class);

  @Value("${spring.kafka.bootstrap-servers}")
  private String kafkaBootstrapServers;

  static {
    MainMapLookup.setMainArguments("fastag-netc-engine");
  }

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }

  @PostConstruct
  void printKafkaDescription() {
    Properties properties = new Properties();
    logger.info("KafkaBootstrapServers:{}", kafkaBootstrapServers);
    properties.put("bootstrap.servers", kafkaBootstrapServers);
    properties.put("connections.max.idle.ms", 10000);
    properties.put("request.timeout.ms", 5000);
    logger.info("KafkaBootstrapServers : {}", properties.getProperty("bootstrap.servers"));
    try (AdminClient client = AdminClient.create(properties)) {
      DescribeClusterResult clusterRes = client.describeCluster();
      KafkaFuture<String> kf = clusterRes.clusterId();
      String clusterId = kf.get();
      logger.info("KafkaClusterId:{}", clusterId);
      KafkaFuture<Collection<Node>> nodes = clusterRes.nodes();
      logger.info("KafkaClusterNodes : {}", nodes.get());
      ListTopicsResult topics = client.listTopics();
      Set<String> names = topics.names().get();
      if (!names.isEmpty()) {
        logger.info("ClusterTopicNames : {}", names);
      } else {
        logger.info("ClusterTopicNames : is EMPTY");
      }
    } catch (InterruptedException | ExecutionException e) {
      logger.error("Looks like not a valid kafka Exception:", e);
      Thread.currentThread().interrupt();
    }
  }
}
