package com.paytm.acquirer.netc.dto.listParticipant;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RespListParticipant {
  private String respCode;
  private String result;
  private String noOfParticipant;
  private List<Participant> participantList;
}
