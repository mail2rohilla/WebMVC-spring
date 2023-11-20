package com.paytm.acquirer.netc.dto.syncTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeSyncResponse {
    @Schema(description = "Local Time")
    private String localTime;
    @Schema(description = "Server Time")
    private String serverTime;
    @Schema(description = "Tas Local Server Time")
    private String tasLocalServerTime;
}
