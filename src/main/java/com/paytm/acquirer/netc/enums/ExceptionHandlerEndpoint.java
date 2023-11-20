package com.paytm.acquirer.netc.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.paytm.acquirer.netc.util.Utils;

public enum ExceptionHandlerEndpoint {
    PRE_LOAD_TAGS_DATA("/api/exc-handler/exceptionHandler/preLoadTagsData", null);

    private final String endpoint;
    private final Integer value;

    ExceptionHandlerEndpoint(String endpoint, Integer value) {
        this.endpoint = endpoint;
        this.value = value;
    }

    public String getEndpointUrl(String baseUrl) {
        return baseUrl + endpoint;
    }

    @JsonCreator
    public static ExceptionHandlerEndpoint fromValue(int value) {
        return Utils.getEnumFromItsProperty(value, ExceptionHandlerEndpoint.class, ExceptionHandlerEndpoint::getValue);
    }

    @JsonValue
    public int getValue() {
        return value;
    }
}
