package com.paytm.acquirer.netc.dto.common;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.paytm.acquirer.netc.enums.TransactionType;
import lombok.Data;


@Data
public abstract class TransactionXml {

    @JacksonXmlProperty(isAttribute = true)
    private String id;

    @JacksonXmlProperty(isAttribute = true)
    private String note;

    @JacksonXmlProperty(isAttribute = true, localName = "refId")
    private String referenceId;

    @JacksonXmlProperty(isAttribute = true, localName = "refUrl")
    private String referenceUrl;

    @JacksonXmlProperty(isAttribute = true, localName = "ts")
    private String timeStamp;

    @JacksonXmlProperty(isAttribute = true)
    private TransactionType type;

    @JacksonXmlProperty(isAttribute = true, localName = "orgTxnId")
    private String originalTransactionId;

}
