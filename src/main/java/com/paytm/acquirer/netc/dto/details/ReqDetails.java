package com.paytm.acquirer.netc.dto.details;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReqDetails {
    @Schema(description = "Tag ID")
    private String tagId;
    @Schema(description = "Vehicle Tag ID")
    private String vehicleTId;
    @Schema(description = "Vehicle Reg Number")
    private String vehicleRegNo;
}
