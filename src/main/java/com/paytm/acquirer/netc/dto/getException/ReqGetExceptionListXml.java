package com.paytm.acquirer.netc.dto.getException;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.paytm.acquirer.netc.dto.common.BaseXml;
import com.paytm.acquirer.netc.dto.common.TransactionXml;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@JacksonXmlRootElement(localName = "etc:ReqGetExceptionList")
public class ReqGetExceptionListXml extends BaseXml {
    @JacksonXmlProperty(localName = "Txn")
    private GetExceptionTransactionXml transaction;

    @Data
    public static class GetExceptionTransactionXml extends TransactionXml {
        @JacksonXmlElementWrapper(localName = "ExceptionList")
        @JacksonXmlProperty(localName = "Exception")
        private List<Exceptions> exceptionList;
    }

    @Data @NoArgsConstructor
    @AllArgsConstructor
    public static class Exceptions {
        @JacksonXmlProperty(localName = "excCode", isAttribute = true)
        private String exceptionCode;
    }
}
