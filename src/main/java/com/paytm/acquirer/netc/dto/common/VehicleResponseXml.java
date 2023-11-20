package com.paytm.acquirer.netc.dto.common;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

import java.util.List;

@Data
public class VehicleResponseXml extends ResponseXml {
    @JacksonXmlProperty(localName = "Vehicle")
    private VehicleXml vehicle;

    @Data
    public static class VehicleXml {
        @JacksonXmlProperty(isAttribute = true, localName = "errCode")
        private String errorCode;

        @JacksonXmlProperty(localName = "VehicleDetails")
        @JacksonXmlElementWrapper(useWrapping = false)
        private List<VehicleDetailsXml> vehicleDetailsList;
    }
}
