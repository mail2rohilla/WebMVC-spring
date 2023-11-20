package com.paytm.acquirer.netc.dto.details;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.paytm.acquirer.netc.dto.common.BaseXml;
import com.paytm.acquirer.netc.dto.common.VehicleTransactionXml;
import lombok.Data;

/**
 * Request Details XML DTO
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JacksonXmlRootElement(localName = "etc:ReqDetails")
public class ReqDetailsXml extends BaseXml {

    @JacksonXmlProperty(localName = "Txn")
    private VehicleTransactionXml transaction;
}
