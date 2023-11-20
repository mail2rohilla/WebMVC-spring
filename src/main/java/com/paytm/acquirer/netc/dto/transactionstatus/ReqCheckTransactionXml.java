package com.paytm.acquirer.netc.dto.transactionstatus;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.paytm.acquirer.netc.dto.common.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Request Check Txn XML DTO
 */
@Data
@JacksonXmlRootElement(localName = "etc:ReqChkTxn")
public class ReqCheckTransactionXml extends BaseXml {

    @JacksonXmlProperty(localName = "Txn")
    private StatusTxnXml transaction;

    @Data
    public static class StatusTxnXml extends TransactionXml {
        @JacksonXmlElementWrapper(localName = "TxnStatusReqList")
        @JacksonXmlProperty(localName = "Status")
        List<StatusXml> transactionStatusListXml;

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class StatusXml {
            @JacksonXmlProperty(isAttribute = true, localName = "txnId")
            private String txnId;
            @JacksonXmlProperty(isAttribute = true, localName = "txnDate")
            private String txnDate;
            @JacksonXmlProperty(isAttribute = true, localName = "merchantId")
            private String merchantId;
            @JacksonXmlProperty(isAttribute = true, localName = "acquirerId")
            private String acquirerId;
        }

    }

}
