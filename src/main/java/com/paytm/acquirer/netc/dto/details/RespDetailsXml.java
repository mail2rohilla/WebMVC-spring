package com.paytm.acquirer.netc.dto.details;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.paytm.acquirer.netc.dto.common.BaseXml;
import com.paytm.acquirer.netc.dto.common.VehicleTxnResponseXml;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "etc:RespDetails")
public class RespDetailsXml extends BaseXml {

    @JacksonXmlProperty(localName = "Txn")
    private VehicleTxnResponseXml transaction;
}
