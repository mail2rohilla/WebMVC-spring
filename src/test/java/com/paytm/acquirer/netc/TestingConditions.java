package com.paytm.acquirer.netc;

import com.paytm.acquirer.netc.enums.RetrialType;
import lombok.Data;

import static com.paytm.acquirer.netc.util.Constants.SUCCESS_RESPONSE_CODE;
import static com.paytm.acquirer.netc.util.Constants.TRANSACTION_ACCEPTED;

@Data
public class TestingConditions {
  private boolean isTimeSyncError;
  private RetrialType retryType = RetrialType.NORMAL_RETRY;
  private boolean checkTxmMaxRetryLimit;
  private boolean maxRetryLimit;
  private boolean emptyValidChkTxnList;
  private boolean resultFailure;
  private boolean duplicateTxn;
  private String txnStatus = TRANSACTION_ACCEPTED;
  private String responseCode = SUCCESS_RESPONSE_CODE;
  private boolean diffDto;
  private boolean diffErrorResponse;
  private int totalMsgNumber = 1;
  private boolean testRequestApi;
}
