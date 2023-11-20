package com.paytm.acquirer.netc.dto.transactionstatus;

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
@JacksonXmlRootElement(localName = "etc:RespChkTxn")
@JsonIgnoreProperties(ignoreUnknown = true)
public class RespCheckTransactionXml extends BaseXml {
    @JacksonXmlProperty(localName = "Txn")
    private CheckTxnTransactionXml checkTxnTransactionXml;

    @Data
    public static class CheckTxnTransactionXml extends TransactionXml {
        @JacksonXmlProperty(localName = "Resp")
        private ChkTxnRespXml response;
    }

    @Data
    public static class ChkTxnRespXml extends ResponseXml {
        // Typo in localName is intentional and is used as is by NETC
        @JacksonXmlProperty(isAttribute = true, localName = "sucessReqCnt")
        private int successRequestCount;

        @JacksonXmlElementWrapper(localName = "TxnStatusReqList")
        @JacksonXmlProperty(localName = "Status")
        private List<StatusXml> statusList;
    }

    @Data
    public static class StatusXml {
        @JacksonXmlProperty(isAttribute = true, localName = "txnId")
        private String txnId;

        @JacksonXmlProperty(isAttribute = true, localName = "txnDate")
        private String txnDate;

        @JacksonXmlProperty(isAttribute = true, localName = "merchantId")
        private String merchantId;

        @JacksonXmlProperty(isAttribute = true, localName = "acquirerId")
        private String acquirerId;

        @JacksonXmlProperty(isAttribute = true, localName = "result")
        private String result;

        @JacksonXmlProperty(isAttribute = true, localName = "errCode")
        private String errCode;

        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "TxnList")
        private List<TransactionListXml> transactionList;
    }

    @Data
    public static class TransactionListXml {
        @JacksonXmlProperty(isAttribute = true, localName = "txnStatus")
        private String txnStatus;

        @JacksonXmlProperty(isAttribute = true, localName = "txnReaderTime")
        private String txnReaderTime;

        @JacksonXmlProperty(isAttribute = true, localName = "txnType")
        private String txnType;

        @JacksonXmlProperty(isAttribute = true, localName = "txnReceivedTime")
        private String txnReceivedTime;

        @JacksonXmlProperty(isAttribute = true, localName = "payeeErrCode")
        private String payeeErrorCode;

        @JacksonXmlProperty(isAttribute = true, localName = "respCode")
        private String responseCode;
    }
}
