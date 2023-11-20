package com.paytm.acquirer.netc.dto.syncTime;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.paytm.acquirer.netc.dto.common.BaseXml;
import lombok.Data;

@Data
@JacksonXmlRootElement(localName = "etc:ReqSyncTime")
public class ReqSyncTimeXml extends BaseXml {
}
