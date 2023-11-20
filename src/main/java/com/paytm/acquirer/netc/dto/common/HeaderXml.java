package com.paytm.acquirer.netc.dto.common;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;


@Data
public class HeaderXml {
    @JacksonXmlProperty(isAttribute = true, localName = "ver")
    private String version;

    @JacksonXmlProperty(isAttribute = true, localName = "ts")
    private String timeStamp;

    @JacksonXmlProperty(isAttribute = true, localName = "orgId")
    private String organizationId;

    @JacksonXmlProperty(isAttribute = true, localName = "msgId")
    private String messageId;
}
