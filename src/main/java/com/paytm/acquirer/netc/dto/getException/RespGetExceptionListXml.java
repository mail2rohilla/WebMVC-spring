package com.paytm.acquirer.netc.dto.getException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.paytm.acquirer.netc.dto.common.BaseXml;
import com.paytm.acquirer.netc.dto.common.GetExceptionResponseXml;
import com.paytm.acquirer.netc.dto.common.TransactionXml;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "etc:RespGetExceptionList")
public class RespGetExceptionListXml extends BaseXml {
    @JacksonXmlProperty(localName = "Txn")
    private RespGetExceptionTransactionXml transaction;

    @Data
    public static class RespGetExceptionTransactionXml extends TransactionXml {
        @JacksonXmlProperty(localName = "Resp")
        private GetExceptionResponseXml response;
    }
}
