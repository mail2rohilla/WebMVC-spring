package com.paytm.acquirer.netc.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.paytm.acquirer.netc.util.Utils;

public enum HandlerType {
    AUTO_RETRY(0), MANUAL_RETRY(1), AUTO_BLACK_LIST(2);

    private Integer value;

    HandlerType(Integer value) {
        this.value = value;
    }

    @JsonCreator
    public static HandlerType fromValue(Integer value) {
        return Utils.getEnumFromItsProperty(value, HandlerType.class, HandlerType::getValue);
    }

    @JsonValue
    public Integer getValue() {
        return value;
    }

}
