package com.paytm.acquirer.netc.dto.health;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Map;

@Getter
@Setter
public class HealthResponse implements Serializable {

    private static final long serialVersionUID = -8387287044732853789L;
    @Schema(description = "ProgramName")
    private String programName;
    @Schema(description = "Version")
    private String version;
    @Schema(description = "Release")
    private String release;
    @Schema(description = "Datetime")
    private long datetime;
    @Schema(description = "Status")
    private String status = "success";
    @Schema(description = "Code")
    private Integer code = 200;
    @Schema(description = "Message")
    private String message = "OK";
    @Schema(description = "Dependent Service")
    private transient Map<String, DependentService> dependentService;
    @Schema(description = "Data")
    private Data data = new Data();

    @Setter
    @Getter
    public static class DependentService {
        @Schema(description = "Status")
        private String status;
        @Schema(description = "Code")
        private Integer code;
        @Schema(description = "Message")
        private String message;
        @Schema(description = "Duration")
        private double duration;
    }

    @Getter
    @Setter
    public class Data implements Serializable {
        private static final long serialVersionUID = 965818185679902105L;
        private double duration;
        private String message;
    }
}
