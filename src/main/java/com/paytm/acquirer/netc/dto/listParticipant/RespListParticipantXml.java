package com.paytm.acquirer.netc.dto.listParticipant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.paytm.acquirer.netc.dto.common.BaseXml;
import com.paytm.acquirer.netc.dto.common.TransactionXml;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@JacksonXmlRootElement(localName = "etc:RespListParticipant")
@JsonIgnoreProperties(ignoreUnknown = true)
public class RespListParticipantXml extends BaseXml {
  
  @JacksonXmlProperty(localName = "Txn")
  private RespListParticipantXml.RespListParticipantTransactionXml transaction;
  
  @Data
  public static class RespListParticipantTransactionXml extends TransactionXml {
    @JacksonXmlProperty(localName = "Resp")
    private RespListParticipantRespXml resp;
    
  }
  
  @Data
  public static class RespListParticipantRespXml  {
    
    @JacksonXmlProperty(isAttribute = true, localName = "ts")
    private String timeStamp;
  
    @JacksonXmlProperty(isAttribute = true, localName = "result")
    private String result;
    
    @JacksonXmlProperty(isAttribute = true, localName = "respCode")
    private String respCode;
    
    @JacksonXmlProperty(isAttribute = true, localName = "NoOfParticipant")
    private String NoOfParticipant;
    
    @JacksonXmlElementWrapper(localName = "ParticipantList")
    @JacksonXmlProperty(localName = "Participant")
    List<ParticipantXml> participantList;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ParticipantXml {

      @JacksonXmlProperty(isAttribute = true, localName = "name")
      private String name;
  
      @JacksonXmlProperty(isAttribute = true, localName = "shortCode")
      private String shortCode;
  
      @JacksonXmlProperty(isAttribute = true, localName = "errCode")
      private String errCode;
  
      @JacksonXmlProperty(isAttribute = true, localName = "acquirerIin")
      private String acquirerIin;
  
      @JacksonXmlProperty(isAttribute = true, localName = "issuerIin")
      private String issuerIin;
  
      @JacksonXmlProperty(isAttribute = true, localName = "role")
      private String role;
    }
  }
}
