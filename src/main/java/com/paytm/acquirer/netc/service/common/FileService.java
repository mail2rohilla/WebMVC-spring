package com.paytm.acquirer.netc.service.common;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Service
public class FileService {

  public byte[] readFile(String path) throws IOException {
    return Files.readAllBytes(Paths.get(path));
  }
}
