package com.paytm.acquirer.netc.dto.common;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;


@Data
public class RespPayResponseXml {
    @JacksonXmlProperty(isAttribute = true)
    private String merchantId;

    @JacksonXmlProperty(localName = "Ref")
    private PaymentRefXml paymentRef;

    @JacksonXmlProperty(isAttribute = true, localName = "ts")
    private String timeStamp;

    @JacksonXmlProperty(isAttribute = true)
    private String result;

    @JacksonXmlProperty(isAttribute = true, localName = "respCode")
    private String responseCode;
}
