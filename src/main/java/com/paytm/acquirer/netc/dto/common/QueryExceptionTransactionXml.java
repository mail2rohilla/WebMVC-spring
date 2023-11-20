package com.paytm.acquirer.netc.dto.common;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
public class QueryExceptionTransactionXml extends TransactionXml {
    @JacksonXmlElementWrapper(localName = "ExceptionList")
    @JacksonXmlProperty(localName = "Exception")
    private List<Exceptions> exceptionList;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Exceptions {
        @JacksonXmlProperty(localName = "excCode", isAttribute = true)
        private String exceptionCode;

        @JacksonXmlProperty(isAttribute = true)
        private String lastFetchTime;
    }
}
