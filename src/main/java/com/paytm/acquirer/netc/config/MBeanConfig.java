package com.paytm.acquirer.netc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jmx.export.MBeanExporter;
import org.springframework.jmx.export.annotation.AnnotationMBeanExporter;


@Configuration

public class MBeanConfig {
  @Bean
  public MBeanExporter exporter() {
    final MBeanExporter exporter = new AnnotationMBeanExporter();
    exporter.setAutodetect(true);
    exporter.setExcludedBeans(new String[]{"slaveDataSource", "masterDataSource"});
    return exporter;
  }
}