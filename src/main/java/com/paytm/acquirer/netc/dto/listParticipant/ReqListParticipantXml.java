package com.paytm.acquirer.netc.dto.listParticipant;

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
@JacksonXmlRootElement(localName = "etc:ReqListParticipant")
public class ReqListParticipantXml extends BaseXml {
  
  @JacksonXmlProperty(localName = "Txn")
  private ReqListTransactionXml transaction;
  
  @Data
  public static class ReqListTransactionXml extends TransactionXml {
     @JacksonXmlElementWrapper(localName = "ParticipantList")
     @JacksonXmlProperty(localName = "Participant")
     private List<ParticipantXml> participantList;
  
     @Data
     @AllArgsConstructor
     @NoArgsConstructor
     public static class ParticipantXml {
  
       @JacksonXmlProperty(isAttribute = true, localName = "shortCode")
       private String shortCode;
       
     }
  }
}
