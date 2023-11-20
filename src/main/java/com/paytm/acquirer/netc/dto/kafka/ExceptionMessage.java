package com.paytm.acquirer.netc.dto.kafka;

import com.paytm.acquirer.netc.enums.ListType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import reactor.util.annotation.Nullable;

@Data
@AllArgsConstructor
@Builder
public class ExceptionMessage {
    private final ListType type;
    private final String msgId;
    private final String lastSuccessTime;
    private final String exceptionMapKey;
    private final String exceptionSetKeyPrefix;
    private final Integer totalTagSetsInRedis;
    @Nullable
    private final ExceptionMetaInfo additionalParam;
}
