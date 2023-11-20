package com.paytm.acquirer.netc.dto.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExceptionMetaInfo {
  @Schema(description = "Plaza ID")
  @JsonProperty("plazaIds")
  List<String> plazaIds;

  @Schema(description = "Init For Data Sync")
  @JsonProperty("initForDataSync")
  private Integer initForDataSync;

  @Schema(description = "File Type")
  @JsonProperty("file_type")
  String fileType;
}

