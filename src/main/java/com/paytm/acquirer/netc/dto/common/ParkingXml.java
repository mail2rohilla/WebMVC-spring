package com.paytm.acquirer.netc.dto.common;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

@Data
public class ParkingXml {
    @JacksonXmlProperty(isAttribute = true)
    private String floor;
    @JacksonXmlProperty(isAttribute = true)
    private String zone;
    @JacksonXmlProperty(isAttribute = true)
    private String slotId;
    @JacksonXmlProperty(isAttribute = true)
    private String readerId;
}
