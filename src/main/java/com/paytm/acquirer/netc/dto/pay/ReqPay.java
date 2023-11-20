package com.paytm.acquirer.netc.dto.pay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.paytm.acquirer.netc.dto.common.VehicleDetails;
import com.paytm.acquirer.netc.enums.PlazaCategory;
import com.paytm.acquirer.netc.enums.PlazaType;
import com.paytm.acquirer.netc.enums.TransactionType;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReqPay {
    private String txnReferenceId;
    private String txnTime;
    private TransactionType txnType;
    private String plazaId;
    private String plazaName;
    private String plazaGeoCode;
    private PlazaType plazaType;
    private String note;

    private String laneId;
    private String laneDirection;

    private String tagReadTime;
    private String tagId;

    private String avc;
    private String wim;
    private String amount;
    private String acquirerId;
    private String plazaTxnId;
    private String bankId;

    private String netcTxnId;
    private Integer localRetry = 0;

    private VehicleDetails vehicleDetails;
    private boolean hardRetry = false;
    private PlazaCategory plazaCategory = PlazaCategory.TOLL;
    private String floor;
    private String zone;
    private String slotId;
    private String readerId;
    private String bbpsTransactionId;
    private String txnStatus;
}
