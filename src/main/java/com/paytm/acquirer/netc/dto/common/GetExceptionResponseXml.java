package com.paytm.acquirer.netc.dto.common;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

import java.util.List;

@Data
public class GetExceptionResponseXml extends ResponseXml {
    @JacksonXmlProperty(localName = "msgNum", isAttribute = true)
    private int messageNumber;

    @JacksonXmlProperty(localName = "totalMsg", isAttribute = true)
    private int totalMessage;

    @JacksonXmlProperty(localName = "totalTagsInMsg", isAttribute = true)
    private int totalTagsInMessage;
    @JacksonXmlProperty(localName = "totalTagsInResponse", isAttribute = true)
    private int totalTagsInResponse;

    @JacksonXmlProperty(localName = "Exception")
    @JacksonXmlElementWrapper(useWrapping = false)
    private List<Exceptions> exceptionList;

    @Data
    public static class Exceptions {
        @JacksonXmlProperty(localName = "excCode", isAttribute = true)
        private String exceptionCode;

        @JacksonXmlProperty(localName = "desc", isAttribute = true)
        private String description;

        @JacksonXmlProperty(isAttribute = true)
        private String priority;

        @JacksonXmlProperty(isAttribute = true)
        private String result;

        @JacksonXmlProperty(localName = "errCode", isAttribute = true)
        private String errorCode;

        @JacksonXmlProperty(isAttribute = true)
        private String totalTag;

        @JacksonXmlProperty(localName = "lastupdatedTime", isAttribute = true)
        private String lastUpdatedTime;

        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "Tag")
        private List<Tag> tagList;
    }

    @Data
    public static class Tag {
        @JacksonXmlProperty(isAttribute = true)
        private String tagId;
    }
}
