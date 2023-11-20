package com.paytm.acquirer.netc.adapter;

import com.paytm.acquirer.netc.dto.transactionstatus.ReqCheckTransactionXml;
import com.paytm.acquirer.netc.dto.transactionstatus.TransactionReq;
import com.paytm.acquirer.netc.enums.NetcEndpoint;
import com.paytm.acquirer.netc.enums.TransactionType;
import com.paytm.acquirer.netc.service.common.MetadataService;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.stream.Collectors;

@UtilityClass
public class CheckTransactionAdapter {
    public static ReqCheckTransactionXml convertTransactionCheckToXmlDto(List<TransactionReq> transactionReqList, MetadataService metadataService, String requestTime) {
        if (transactionReqList == null) {
            return null;
        }
        // create Txn
        ReqCheckTransactionXml.StatusTxnXml statusTxnXml = new ReqCheckTransactionXml.StatusTxnXml();
        statusTxnXml.setTimeStamp(requestTime);
        statusTxnXml.setType(TransactionType.ChkTxn);
        List<ReqCheckTransactionXml.StatusTxnXml.StatusXml> transactionRequestXmlList = transactionReqList.stream()
                .map(transactionReq -> new ReqCheckTransactionXml.StatusTxnXml.StatusXml(transactionReq.getTxnId(), transactionReq.getTxnDate(),
                        transactionReq.getMerchantId(), transactionReq.getAcquirerId()))
                .collect(Collectors.toList());

        statusTxnXml.setTransactionStatusListXml(transactionRequestXmlList);
        metadataService.updateTransaction(statusTxnXml, NetcEndpoint.REQ_TXN_STATUS);

        ReqCheckTransactionXml transactionStatusReqXml = new ReqCheckTransactionXml();
        transactionStatusReqXml.setTransaction(statusTxnXml);
        return transactionStatusReqXml;
    }
}
