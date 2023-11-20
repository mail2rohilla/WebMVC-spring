package com.paytm.acquirer.netc.dto.queryException;

import com.paytm.acquirer.netc.dto.common.QueryExceptionResponseXml;
import com.paytm.acquirer.netc.util.Utils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RedisTag {
    private String code;
    private String tagId;
    private String operation;
    private String updatedTime;

    public RedisTag(String code, QueryExceptionResponseXml.Tag tag) {
        this.code = code;
        this.tagId = tag.getTagId();
        this.operation = tag.getOperation();
        this.updatedTime = tag.getUpdatedTime();
    }

    public RedisTag(ExceptionDiffFileDto exception) {
        this.code = exception.getExceptionCode();
        this.tagId = exception.getTagId();
        this.operation = exception.getOperation();
        this.updatedTime = Utils.getFormattedDateWithoutOffset(exception.getUpdatedTime().toLocalDateTime());
    }
}
