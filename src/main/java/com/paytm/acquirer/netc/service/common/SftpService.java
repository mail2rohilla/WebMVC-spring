package com.paytm.acquirer.netc.service.common;

import com.jcraft.jsch.*;
import com.paytm.acquirer.netc.dto.getException.SFTPConfig;
import com.paytm.acquirer.netc.exception.NetcEngineException;
import com.paytm.acquirer.netc.util.Constants;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SftpService {

  private static final Logger log = LoggerFactory.getLogger(SftpService.class);

  private static final String STRICT_HOST_KEY_CHECKING_KEY = "StrictHostKeyChecking";
  private static final String STRICT_HOST_KEY_CHECKING_VALUE = "no";
  private static final String KEY = "key";
  private final SFTPConfig sftpConfig;


  private JSch jsch = null;

  public Session createSession() {

    Session session = null;
    jsch = new JSch();

    log.info("[SftpServiceImpl.getFile] properties {}", sftpConfig);
    try {
      if (StringUtils.isNotBlank(sftpConfig.getKeyPath())) {
        String key =
          StreamUtils.copyToString(
            new ClassPathResource(sftpConfig.getKeyPath()).getInputStream(),
            Charset.defaultCharset());
        log.info("connecting to the SFTP using key");
        jsch.addIdentity(KEY, key.getBytes(), null, sftpConfig.getPassword().getBytes());
        session = jsch.getSession(sftpConfig.getUsername(), sftpConfig.getHost(), sftpConfig.getPort());
        session.setConfig("PreferredAuthentications", "publickey,password,keyboard-interactive");
      } else {
        log.info("connecting to the SFTP using username and password");
        session = jsch.getSession(sftpConfig.getUsername(), sftpConfig.getHost(), sftpConfig.getPort());
        session.setPassword(sftpConfig.getPassword());
        session.setConfig("PreferredAuthentications", "password,publickey,keyboard-interactive");
      }

      session.setConfig(STRICT_HOST_KEY_CHECKING_KEY, STRICT_HOST_KEY_CHECKING_VALUE);
      session.connect();
    }
    catch (JSchException | IOException e) {
      log.error("[SftpServiceImpl.createSession] error while creating session", e);
    }

    return session;
  }

  public ChannelSftp creatSFTPChannel(Session session) {
    ChannelSftp channelSftp;
    try {
      channelSftp = (ChannelSftp) session.openChannel(Constants.CHANNEL_DESC);
      channelSftp.connect();
    }
    catch (JSchException e) {
      log.error("Error while opening sftp channel", e);
      throw new NetcEngineException("Error while opening SFTP channel using the current session");
    }
    return channelSftp;
  }

  public List<String> getFileNamesOnSFTP(ChannelSftp channelSftp, String remotePath) {
    try {
      log.info("Fetching files from SFTP from path {}", remotePath);
      List<ChannelSftp.LsEntry> sftpFiles = channelSftp.ls(remotePath);
      return sftpFiles.stream()
        .map(ChannelSftp.LsEntry::getFilename)
        .collect(Collectors.toList());
    }
    catch (SftpException e) {
      log.error("[getNumOfFilesONSFTP] Error while fetching files from sftp from path {}", e,  remotePath);
      throw new NetcEngineException("[getNumOfFilesONSFTP] Error while fetching files from sftp");
    }
  }

}
