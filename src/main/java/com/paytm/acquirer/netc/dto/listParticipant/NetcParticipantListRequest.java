package com.paytm.acquirer.netc.dto.listParticipant;

import lombok.Data;

import java.util.List;
@Data
public class NetcParticipantListRequest {
  private List<String> issuerIin;
}
