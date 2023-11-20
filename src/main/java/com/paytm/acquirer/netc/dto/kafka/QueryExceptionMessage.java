package com.paytm.acquirer.netc.dto.kafka;

import lombok.Data;

@Data
public class QueryExceptionMessage {
    private String msgId;
    private Long totalTags;
    private String fetchTime;
    private String lastSuccessTime;
    private String key;
    private String type;
    private Boolean isDiffAfterInit;
    private String lastDiffMsgId;
    private String uid;
    private Boolean isEmptyDiff;
    private Long netcEngineClockOffset;

    private Boolean isDiffViaSftp;
    private String lastTagUpdatedTime;
    private String lastSftpFileName;

}
