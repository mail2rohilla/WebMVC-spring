package com.paytm.acquirer.netc.dto.getException;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
@Setter
@ConfigurationProperties("sftp.server")
public class SFTPConfig {

  private String username;

  private String host;

  private Integer port;

  private String keyPath;

  private String password;

  private String remoteDir;

  private String localDir;

  // * INIT paths
  private String fileName;

  private String encryptedFolder;

  private String decryptedFolder;

  // * DIFF paths
  private String diffFileName;

  private String encryptedDiffFolder;

  private String decryptedDiffFolder;
}
