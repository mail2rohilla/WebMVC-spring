package com.paytm.acquirer.netc.dto.common;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

@Data
public class AmountXml {
    @JacksonXmlProperty(isAttribute = true)
    private String value;
    @JacksonXmlProperty(isAttribute = true)
    private String curr;
}
