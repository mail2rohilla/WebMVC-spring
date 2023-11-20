package com.paytm.acquirer.netc.dto.common;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

@Data
public class VehicleTransactionXml extends TransactionXml {
    @JacksonXmlProperty(localName = "Vehicle")
    private VehicleXml vehicle;

    @Data
    public static class VehicleXml {
        @JacksonXmlProperty(isAttribute = true)
        private String tagId;

        @JacksonXmlProperty(isAttribute = true, localName = "TID")
        private String vehicleTId;

        /**
         * Vehicle class captured by the AVC machine at plaza
         */
        @JacksonXmlProperty(isAttribute = true, localName = "avc")
        private String avc;

        @JacksonXmlProperty(isAttribute = true, localName = "vehicleRegNo")
        private String vehicleRegNo;
    }
}
