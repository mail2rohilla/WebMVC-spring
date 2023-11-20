package com.paytm.acquirer.netc.dto.common;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

@Data
public class ReaderVerificationResultXml {
    @JacksonXmlProperty(isAttribute = true)
    private String tsRead;
    @JacksonXmlProperty(isAttribute = true)
    private String signData;
    @JacksonXmlProperty(isAttribute = true)
    private String signAuth;
    @JacksonXmlProperty(isAttribute = true)
    private String tagVerified;
    @JacksonXmlProperty(isAttribute = true)
    private String procRestrictionResult;
    @JacksonXmlProperty(isAttribute = true)
    private String vehicleAuth;
    @JacksonXmlProperty(isAttribute = true)
    private String publicKeyCVV;
    @JacksonXmlProperty(isAttribute = true)
    private String txnCounter;
    @JacksonXmlProperty(isAttribute = true)
    private String txnStatus;
}
