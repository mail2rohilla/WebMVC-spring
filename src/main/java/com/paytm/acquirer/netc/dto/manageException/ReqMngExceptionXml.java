package com.paytm.acquirer.netc.dto.manageException;

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
@JacksonXmlRootElement(localName = "etc:ReqMngException")
public class ReqMngExceptionXml extends BaseXml {
    @JacksonXmlProperty(localName = "Txn")
    private MngExceptionTransactionXml transaction;

    @Data
    public static class MngExceptionTransactionXml extends TransactionXml {
        @JacksonXmlElementWrapper(localName = "TagList")
        @JacksonXmlProperty(localName = "Tag")
        private List<Tag> tagList;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Tag {
        @JacksonXmlProperty(localName = "op", isAttribute = true)
        private String operation;
        @JacksonXmlProperty(isAttribute = true)
        private String tagId;
        @JacksonXmlProperty(isAttribute = true)
        private int seqNum;
        @JacksonXmlProperty(localName = "excCode", isAttribute = true)
        private String exceptionCode;
    }
}
