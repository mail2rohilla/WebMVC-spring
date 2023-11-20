package com.paytm.acquirer.netc.dto.transactionstatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder
@NoArgsConstructor @AllArgsConstructor
public class TransactionReq {
    private String txnId;
    private String txnDate;
    private String merchantId;
    private String acquirerId;
}
