package com.paytm.acquirer.netc.dto.queryException;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.paytm.acquirer.netc.dto.common.BaseXml;
import com.paytm.acquirer.netc.dto.common.QueryExceptionTransactionXml;
import lombok.Data;

@Data
@JacksonXmlRootElement(localName = "etc:ReqQueryExceptionList")
public class ReqQueryExceptionListXml extends BaseXml {
    @JacksonXmlProperty(localName = "Txn")
    private QueryExceptionTransactionXml transaction;
}
