package com.paytm.acquirer.netc.dto.syncTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.paytm.acquirer.netc.dto.common.BaseXml;
import com.paytm.acquirer.netc.dto.common.ResponseXml;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "etc:RespSyncTime")
public class RespSyncTimeXml extends BaseXml {
    @JacksonXmlProperty(localName = "Resp")
    private TimeResponseXml responseXml;

    @Data
    public static class TimeResponseXml extends ResponseXml {
        @JacksonXmlProperty(localName = "Time")
        private Time netcTime;
    }

    @Data
    public static class Time {
        @JacksonXmlProperty(isAttribute = true)
        private String serverTime;
    }
}
