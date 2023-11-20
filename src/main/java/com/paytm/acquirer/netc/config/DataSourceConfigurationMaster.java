package com.paytm.acquirer.netc.config;

import com.paytm.acquirer.netc.db.entities.AsyncTransaction;
import com.paytm.transport.db.entity.ClientOperationMessageEntity;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
  basePackages = {
    "com.paytm.acquirer.netc.db.repositories.master",
    "com.paytm.transport.db.repository.master"
  })
public class DataSourceConfigurationMaster {
  private static final String MASTER_PREFIX = "acquirer.db.master";

  @Bean
  @Primary
  @ConfigurationProperties(MASTER_PREFIX)
  public DataSourceProperties masterDataSourceProperties() {
    return new DataSourceProperties();
  }

  @Bean
  @Primary
  @Qualifier("masterDataSource")
  @ConfigurationProperties(MASTER_PREFIX)
  public DataSource masterDataSource() {
    return masterDataSourceProperties()
      .initializeDataSourceBuilder()
      .type(HikariDataSource.class)
      .build();
  }

  @Bean(name = "entityManagerFactory")
  @Primary
  public LocalContainerEntityManagerFactoryBean masterEntityManagerFactory(
    EntityManagerFactoryBuilder builder) {
    return builder
      .dataSource(masterDataSource())
      .packages(AsyncTransaction.class, ClientOperationMessageEntity.class)
      .build();
  }

  @Bean(name = "transactionManager")
  @Primary
  public PlatformTransactionManager transactionManager(
    @Qualifier("entityManagerFactory") EntityManagerFactory entityManagerFactory) {
    return new JpaTransactionManager(entityManagerFactory);
  }
}
