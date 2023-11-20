package com.paytm.acquirer.netc.dto.manageException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.paytm.acquirer.netc.dto.common.BaseXml;
import com.paytm.acquirer.netc.dto.common.ResponseXml;
import com.paytm.acquirer.netc.dto.common.TransactionXml;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "etc:RespMngException")
public class RespMngExceptionXml extends BaseXml {
    @JacksonXmlProperty(localName = "Txn")
    private MngExceptionTransactionXml transaction;

    @Data
    public static class MngExceptionTransactionXml extends TransactionXml {
        @JacksonXmlProperty(localName = "Resp")
        private MngExceptionResponseXml response;
    }

    @Data @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MngExceptionResponseXml extends ResponseXml {
        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "Tag")
        private List<Tag> tags;
    }

    @Data
    public static class Tag {
        @JacksonXmlProperty(localName = "op", isAttribute = true)
        private String operation;
        @JacksonXmlProperty(isAttribute = true)
        private String tagId;
        @JacksonXmlProperty(isAttribute = true)
        private String seqNum;
        @JacksonXmlProperty(isAttribute = true)
        private String result;
        @JacksonXmlProperty(localName = "errCode", isAttribute = true)
        private String errorCode;
    }
}
