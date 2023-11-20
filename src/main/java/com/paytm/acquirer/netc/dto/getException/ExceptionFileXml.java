package com.paytm.acquirer.netc.dto.getException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;

@Data
@JacksonXmlRootElement(localName = "Envelope")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExceptionFileXml {

  @JacksonXmlProperty(localName = "OrgContent")
  private String orgContent;

  @JacksonXmlProperty(localName = "Signature")
  private String signature;

  @JacksonXmlProperty(localName = "Certificate")
  private String certificate;


}
