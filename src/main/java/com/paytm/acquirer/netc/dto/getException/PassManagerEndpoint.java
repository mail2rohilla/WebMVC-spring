package com.paytm.acquirer.netc.dto.getException;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
@Setter
@ConfigurationProperties("pass-manager")
public class PassManagerEndpoint {

  private String baseUrl;

  private String saveActivePassForInit;
}
