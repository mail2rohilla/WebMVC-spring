package com.paytm.acquirer.netc.dto.common;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

@Data
public class PaymentRefXml {
    @JacksonXmlProperty(isAttribute = true)
    private String type;
    @JacksonXmlProperty(isAttribute = true)
    private String addr;
    @JacksonXmlProperty(isAttribute = true)
    private String accType;
    @JacksonXmlProperty(isAttribute = true)
    private Float settAmount;
    @JacksonXmlProperty(isAttribute = true)
    private String settCurrency;
    @JacksonXmlProperty(isAttribute = true)
    private String approvalNum;
    @JacksonXmlProperty(isAttribute = true)
    private String errCode;
    @JacksonXmlProperty(isAttribute = true)
    private String avalBal;
    @JacksonXmlProperty(isAttribute = true)
    private String ledgerBal;
    @JacksonXmlProperty(isAttribute = true)
    private String maskedAccountNumber;
    @JacksonXmlProperty(isAttribute = true)
    private String customerName;
}
