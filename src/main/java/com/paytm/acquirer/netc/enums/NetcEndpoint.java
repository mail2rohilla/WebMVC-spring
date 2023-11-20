package com.paytm.acquirer.netc.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.paytm.acquirer.netc.util.Utils;

public enum NetcEndpoint {
    REQ_DETAILS("/requestDetail", 0),
    REQ_PAY("/reqPayService", 1),
    GET_EXCEPTION_LIST("/getExceptionList", 2),
    QUERY_EXCEPTION_LIST("/queryException", 3),
    REQ_TXN_STATUS("/checkTxnStatus", 4),
    MNG_EXCEPTION("/manageException", 5),
    SYNC_TIME("/syncTimeRequest", 6),
    LIST_PARTICIPANT("/listParticipant",7),

    RESP_PAY("",10),
    RESP_GET_EXCEPTION_LIST("",11),
    RESP_QUERY_EXCEPTION_LIST("",12);

    private final String endpoint;
    private final Integer value;

    NetcEndpoint(String endpoint, Integer value) {
        this.endpoint = endpoint;
        this.value = value;
    }

    public String getEndpointUrl(String baseUrl) {
        return baseUrl + endpoint;
    }

    @JsonCreator
    public static NetcEndpoint fromValue(int value) {
        return Utils.getEnumFromItsProperty(value, NetcEndpoint.class, NetcEndpoint::getValue);
    }

    @JsonValue
    public int getValue() {
        return value;
    }
}
