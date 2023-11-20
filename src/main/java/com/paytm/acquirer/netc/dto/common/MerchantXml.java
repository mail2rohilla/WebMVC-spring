package com.paytm.acquirer.netc.dto.common;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.paytm.acquirer.netc.enums.PlazaType;
import lombok.Data;

@Data
public class MerchantXml {
    @JacksonXmlProperty(isAttribute = true)
    private String id;
    @JacksonXmlProperty(isAttribute = true)
    private String name;
    @JacksonXmlProperty(isAttribute = true)
    private String geoCode;
    @JacksonXmlProperty(isAttribute = true)
    private String type;
    @JacksonXmlProperty(isAttribute = true)
    private PlazaType subtype;

    @JacksonXmlProperty(localName = "Lane")
    private LaneXml lane;
    @JacksonXmlProperty(localName = "Parking")
    private ParkingXml parking;
    @JacksonXmlProperty(localName = "ReaderVerificationResult")
    private ReaderVerificationResultXml verificationResult;
}
