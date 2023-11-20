package com.paytm.acquirer.netc.dto.efkon;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TagUpdateResponse {

  private String result;

  private String tagId;

  private String blacklistUpdatedAt;

}
