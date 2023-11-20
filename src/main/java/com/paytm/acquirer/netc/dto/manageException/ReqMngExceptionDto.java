package com.paytm.acquirer.netc.dto.manageException;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class ReqMngExceptionDto {
    @Schema(description = "Tag Entry List")
    private List<TagEntry> tagEntryTagList;
    @Schema(description = "Org ID")
    private String orgId;

    @Data
    public static class TagEntry {
        @Schema(description = "Operation")
        private String operation;
        @Schema(description = "Tag ID")
        private String tagId;
        @Schema(description = "Exception Code")
        private String exceptionCode;
    }
}
