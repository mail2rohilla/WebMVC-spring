package com.paytm.acquirer.netc.dto.pay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.paytm.acquirer.netc.dto.common.BaseXml;
import com.paytm.acquirer.netc.dto.common.RespPayResponseXml;
import com.paytm.acquirer.netc.dto.common.RiskScoreTxnXml;
import lombok.Data;

@Data
@JacksonXmlRootElement(localName = "etc:RespPay")
@JsonIgnoreProperties(ignoreUnknown = true)
public class RespPayXml extends BaseXml {
    @JacksonXmlProperty(localName = "Txn")
    private RiskScoreTxnXml riskScoreTxn;

    @JacksonXmlProperty(localName = "Resp")
    private RespPayResponseXml response;
}
