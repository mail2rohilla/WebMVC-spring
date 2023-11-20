package com.paytm.acquirer.netc.service.common;

import com.paytm.acquirer.netc.dto.getException.ExceptionFileXml;
import org.springframework.stereotype.Service;

import java.security.cert.X509Certificate;

@Service
public interface ISignatureService {

  public String signXmlDocument(String serializedXml);

  public boolean signAndVerifyXmlDoc(ExceptionFileXml exceptionFileXml);

  public X509Certificate getCertificateFromFile(String certNodeData);

  public boolean isXmlValid(String serializedXml);
}
