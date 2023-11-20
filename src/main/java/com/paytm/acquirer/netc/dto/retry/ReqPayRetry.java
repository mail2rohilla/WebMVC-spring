package com.paytm.acquirer.netc.dto.retry;

import lombok.Data;

@Data
public class ReqPayRetry {
    private String txnReferenceId;
    private String netcTxnId;
}
