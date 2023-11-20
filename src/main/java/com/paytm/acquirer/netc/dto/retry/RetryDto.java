package com.paytm.acquirer.netc.dto.retry;

import com.paytm.acquirer.netc.enums.NetcEndpoint;
import com.paytm.acquirer.netc.enums.RetrialType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data @Builder
@NoArgsConstructor @AllArgsConstructor
public class RetryDto {
    private NetcEndpoint api;
    private String msgId;
    private Map<String, String> params;
    private RetrialType retrialType;
}
