package com.paytm.acquirer.netc.dto.listParticipant;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Participant {
  private String name;
  private String shortCode;
  private String errCode;
  private String acquirerIin;
  private String issuerIin;
  private String role;
}
