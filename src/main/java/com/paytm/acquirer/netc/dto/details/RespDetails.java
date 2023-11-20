package com.paytm.acquirer.netc.dto.details;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paytm.acquirer.netc.dto.common.VehicleDetails;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RespDetails {
    @Schema(description = "Response Code")
    private String respCode;
    @Schema(description = "Error Code")
    private String errCode;
    @Schema(description = "Error Code Mapping")
    private String errCodeMapping;
    @Schema(description = "Result")
    private String result;
    @Schema(description = "Vehicle Details")
    private List<VehicleDetails> vehicleDetails;
}
