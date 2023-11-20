package com.paytm.acquirer.netc.dto.common;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

import java.util.List;

@Data
public class RiskScoreTxnXml extends TransactionXml {
    @JacksonXmlElementWrapper(localName = "RiskScores")
    @JacksonXmlProperty(localName = "Score")
    List<RiskScoreXml> riskScores;
}
