package com.paytm.acquirer.netc.dto.common;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;


@Data
public abstract class ResponseXml {

    @JacksonXmlProperty(isAttribute = true, localName = "ts")
    private String timeStamp;

    @JacksonXmlProperty(isAttribute = true)
    private String result;

    @JacksonXmlProperty(isAttribute = true, localName = "respCode")
    private String responseCode;

    @JacksonXmlProperty(isAttribute = true, localName = "totReqCnt")
    private int totalRequestCount;

    @JacksonXmlProperty(isAttribute = true, localName = "successReqCnt")
    private int successRequestCount;
}
