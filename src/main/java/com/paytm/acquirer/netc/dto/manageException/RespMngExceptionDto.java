package com.paytm.acquirer.netc.dto.manageException;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
public class RespMngExceptionDto {
    @Schema(description = "Result")
    private String result;
    @Schema(description = "Resp Code")
    private String respCode;
    @Schema(description = "Total Request Count")
    private int totalRequestCount;
    @Schema(description = "Success Request Count")
    private int successRequestCount;
    @Schema(description = "Tag Entries")
    private List<TagEntry> tagEntries;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class TagEntry {
        @Schema(description = "Operation")
        private String operation;
        @Schema(description = "Tag ID")
        private String tagId;
        @Schema(description = "Result")
        private String result;
        @Schema(description = "Error Code")
        private String errCode;
        @Schema(description = "Error Code Mapping")
        private String errCodeMapping;
    }
}