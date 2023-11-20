package com.paytm.acquirer.netc.dto.pay;

import com.paytm.acquirer.netc.enums.Status;
import lombok.Data;

@Data
public class RespPay {
    private String refId;
    private String responseMapping;
    private String result;
    private Status status;
    private String netcTxnId;
    private String netcResponseTime;
    private String errorCodes;
    private String requestTime;
    private String settledPlazaId;

    public String getStatus() {
        return status.getTxnManagerStatus();
    }
}
