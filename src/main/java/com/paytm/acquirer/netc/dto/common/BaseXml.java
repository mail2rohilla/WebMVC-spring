package com.paytm.acquirer.netc.dto.common;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import lombok.Data;

/**
 * Base class for DTOs (Xml root element) containing common properties
 */
@Data
public abstract class BaseXml {
    @JacksonXmlProperty(isAttribute = true, localName = "xmlns:etc")
    private final String namespace = "http://npci.org/etc/schema/";

    @JacksonXmlProperty(localName = "Head")
    private HeaderXml header;
}
