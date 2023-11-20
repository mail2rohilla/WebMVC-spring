package com.paytm.acquirer.netc.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.paytm.acquirer.netc.util.Utils;

public enum Status {
    INITIATING_REQUEST(101, "INITIATING_REQUEST"),
    REQUEST_OK(0, "WAITING_FOR_RESPONSE"),
    RESPONSE_RECEIVED(1, "RESPONSE_RECEIVED"),
    REQUEST_FAILED(2, "REJECTED_BY_NETC"),
    TIMEOUT(3, "TIMEOUT"),
    CRON_TIMEOUT(4, "CRON_TIMEOUT"),

    AUTO_RETRY_CODE(5, "AUTO_RETRY"),
    MANUAL_RETRY_CODE(6, "MANUAL_RETRY"),
    BLACK_LIST_CODE(7, "BLACK_LIST"),
    CRON_TIMEOUT_AUTO_RETRY_CODE(8,"CRON_TIMEOUT_AUTO_RETRY"),
    CRON_TIMEOUT_MANUAL_RETRY_CODE(9,"CRON_TIMEOUT_MANUAL_RETRY"),

    RESPONSE_RECEIVED_SFTP(11, "INIT_SFTP_PROCESSED"),

    EXTERNAL_FAILURE(100, "EXTERNAL_FAILURE"); // Signify that request processing failed by external services


    private int value;
    private String txnManagerStatus;

    Status(int value, String txnManagerStatus) {
        this.value = value;
        this.txnManagerStatus = txnManagerStatus;
    }

    @JsonCreator
    public static Status fromValue(int value) {
        return Utils.getEnumFromItsProperty(value, Status.class, Status::getValue);
    }

    @JsonValue
    public String getTxnManagerStatus() {
        return txnManagerStatus;
    }

    public int getValue() {
        return value;
    }
}
