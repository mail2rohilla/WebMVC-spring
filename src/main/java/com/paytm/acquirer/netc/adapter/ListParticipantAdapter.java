package com.paytm.acquirer.netc.adapter;

import com.paytm.acquirer.netc.db.entities.IinInfo;
import com.paytm.acquirer.netc.dto.listParticipant.Participant;
import com.paytm.acquirer.netc.dto.listParticipant.ReqListParticipantXml;
import com.paytm.acquirer.netc.dto.listParticipant.RespListParticipantXml;
import com.paytm.acquirer.netc.enums.NetcEndpoint;
import com.paytm.acquirer.netc.enums.TransactionType;
import com.paytm.acquirer.netc.service.common.MetadataService;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.paytm.acquirer.netc.util.Constants.SUCCESS_RESPONSE_CODE;

@UtilityClass
public class ListParticipantAdapter {
  
  public static ReqListParticipantXml getListParticipantDto(String requestTimeStamp, MetadataService metadataService) {
    ReqListParticipantXml reqListParticipantXml = new ReqListParticipantXml();
  
    ReqListParticipantXml.ReqListTransactionXml reqListTransactionXml = new ReqListParticipantXml.ReqListTransactionXml();
    reqListTransactionXml.setTimeStamp(requestTimeStamp);
    reqListTransactionXml.setType(TransactionType.ListParticipant);
    
    metadataService.updateTransaction(reqListTransactionXml, NetcEndpoint.LIST_PARTICIPANT);
    reqListTransactionXml.setOriginalTransactionId(metadataService.getOrgTxnId(reqListTransactionXml.getId()));
    
    reqListTransactionXml.setParticipantList(Collections.singletonList(new ReqListParticipantXml.ReqListTransactionXml.ParticipantXml("ALL")));
  
    reqListParticipantXml.setTransaction(reqListTransactionXml);
    return reqListParticipantXml;
  }
  
  public static List<IinInfo> iinInfoMapper(RespListParticipantXml respListParticipantXml) {
    List<IinInfo> iinInfoList = new ArrayList<>();
    
    for(RespListParticipantXml.RespListParticipantRespXml.ParticipantXml participantBank: respListParticipantXml.getTransaction().getResp().getParticipantList()) {
      if(Objects.equals(participantBank.getErrCode(), SUCCESS_RESPONSE_CODE)){
        IinInfo iinInfo = new IinInfo();
        iinInfo.setAcquirerIin(participantBank.getAcquirerIin());
        iinInfo.setActive(true);
        iinInfo.setIssuerIin(participantBank.getIssuerIin());
        iinInfo.setRole(participantBank.getRole());
        iinInfo.setBankName(participantBank.getName());
        iinInfo.setShortCode(participantBank.getShortCode());
        
        iinInfoList.add(iinInfo);
      }
    }
    
    return iinInfoList;
  }
  
  public static Participant getSuccessRespParticipantDto(IinInfo iinInfo) {
    Participant participant = new Participant();
    
    participant.setAcquirerIin(iinInfo.getAcquirerIin());
    participant.setErrCode(SUCCESS_RESPONSE_CODE);
    participant.setIssuerIin(iinInfo.getIssuerIin());
    participant.setName(iinInfo.getBankName());
    participant.setRole(iinInfo.getRole());
    participant.setShortCode(iinInfo.getShortCode());
    
    return participant;
  }
  
  public static Participant getFailureRespParticipantDto(String issuerIin, String errorCode) {
    Participant participant = new Participant();
    
    participant.setErrCode(errorCode);
    participant.setIssuerIin(issuerIin);
    
    return participant;
  }
  
}
