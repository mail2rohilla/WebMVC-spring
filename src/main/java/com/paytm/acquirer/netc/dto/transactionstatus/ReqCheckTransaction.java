package com.paytm.acquirer.netc.dto.transactionstatus;

import lombok.Data;

import java.util.List;

@Data
public class ReqCheckTransaction {
    private List<TransactionReq> transactions;
}
