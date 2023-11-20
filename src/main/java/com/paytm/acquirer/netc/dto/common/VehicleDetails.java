package com.paytm.acquirer.netc.dto.common;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class VehicleDetails {
    @Schema(description = "Tag ID")
    private String tagId;
    @Schema(description = "Reg Number")
    private String regNumber;
    @Schema(description = "Tid")
    private String tid;
    @Schema(description = "Vehicle Class")
    private String vehicleClass;
    @Schema(description = "Tag Status")
    private String tagStatus;
    @Schema(description = "Issue Date")
    private String issueDate;
    @Schema(description = "Exception Codes")
    private List<String> exceptionCodes;
    @Schema(description = "Bank ID")
    private String bankId;
    @Schema(description = "Commercial Vehicle")
    private String commercialVehicle;
}
