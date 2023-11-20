package com.paytm.acquirer.netc.dto.common;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

@Data
public class VehicleTxnResponseXml extends TransactionXml {
    @JacksonXmlProperty(localName = "Resp")
    private VehicleResponseXml response;
}
