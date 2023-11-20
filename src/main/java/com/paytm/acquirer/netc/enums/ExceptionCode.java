package com.paytm.acquirer.netc.enums;

public enum  ExceptionCode {
    HOTLIST("01",4),
    EXEMPTED_VEHICLE_CLASS("02", 2),
    LOW_BALANCE("03", 1),
    INVALID_CARRIAGE("04", 8),
    BLACKLIST("05", 16),
    CLOSED_REPLACED("06", 32);
    
    private String value;
    private Integer binaryValue;
    
    ExceptionCode(String value, Integer binaryValue) {
        this.value = value;
        this.binaryValue = binaryValue;
    }

    public String getValue() {
        return value;
    }
    
    public static Integer getBinaryValue(String value) {
        switch (value) {
            case "01":
                return 4;
            case "02":
                return 2;
            case "03":
                return 1;
            case "04":
                return 8;
            case "05":
                return 16;
            case "06":
                return 32;
            default:
                return 0;
        }
    }

    @Override
    public String toString() {
        return value;
    }
}