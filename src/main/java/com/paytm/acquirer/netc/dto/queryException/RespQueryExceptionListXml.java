package com.paytm.acquirer.netc.dto.queryException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.paytm.acquirer.netc.dto.common.BaseXml;
import com.paytm.acquirer.netc.dto.common.QueryExceptionResponseXml;
import com.paytm.acquirer.netc.dto.common.TransactionXml;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "etc:RespQueryExceptionList")
public class RespQueryExceptionListXml extends BaseXml {
    @JacksonXmlProperty(localName = "Txn")
    private RespQueryExceptionTransactionXml transaction;

    @Data
    public static class RespQueryExceptionTransactionXml extends TransactionXml {
        @JacksonXmlProperty(localName = "Resp")
        private QueryExceptionResponseXml response;
    }
}
