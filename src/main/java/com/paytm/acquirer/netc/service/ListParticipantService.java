package com.paytm.acquirer.netc.service;

import com.paytm.acquirer.netc.adapter.ListParticipantAdapter;
import com.paytm.acquirer.netc.config.properties.RetryProperties;
import com.paytm.acquirer.netc.db.entities.IinInfo;
import com.paytm.acquirer.netc.db.repositories.master.IinInfoMasterRepository;
import com.paytm.acquirer.netc.db.repositories.slave.IinInfoSlaveRepository;
import com.paytm.acquirer.netc.dto.listParticipant.Participant;
import com.paytm.acquirer.netc.dto.listParticipant.ReqListParticipantXml;
import com.paytm.acquirer.netc.dto.listParticipant.RespListParticipant;
import com.paytm.acquirer.netc.dto.listParticipant.RespListParticipantXml;
import com.paytm.acquirer.netc.service.common.MetadataService;
import com.paytm.acquirer.netc.service.common.RedisService;
import com.paytm.acquirer.netc.service.common.SignatureService;
import com.paytm.acquirer.netc.service.common.ValidateService;
import com.paytm.acquirer.netc.service.retry.WrappedNetcClient;
import com.paytm.acquirer.netc.util.Constants;
import com.paytm.acquirer.netc.util.Utils;
import com.paytm.acquirer.netc.util.XmlUtil;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.paytm.acquirer.netc.util.Constants.ALL_PARTICIPANT_CODE;
import static com.paytm.acquirer.netc.util.Constants.PARTICIPANT_NOT_IN_DB_ERROR;
import static com.paytm.acquirer.netc.util.Constants.RESULT_FAILURE;
import static com.paytm.acquirer.netc.util.Constants.RESULT_PARTIAL;
import static com.paytm.acquirer.netc.util.Constants.RESULT_SUCCESS;
import static com.paytm.acquirer.netc.util.Constants.SUCCESS_RESPONSE_CODE;

@Service
@AllArgsConstructor
public class ListParticipantService {
  
  private static final Logger log = LoggerFactory.getLogger(ListParticipantService.class);
  
  private final TimeService timeService;
  private final RetryProperties retryProperties;
  private final SignatureService signatureService;
  private final WrappedNetcClient wrappedNetcClient;
  private final MetadataService metadataService;
  private final IinInfoMasterRepository iinInfoMasterRepository;
  private final IinInfoSlaveRepository iinInfoSlaveRepository;
  private final RedisService redisService;
  private final ValidateService validateService;
  
  public void requestListParticipantRetry(String msgId) {
    int retryCount = 0;
    RespListParticipantXml respListParticipantXml = requestListParticipant(retryCount, msgId);
    
    while(retryCount++ < retryProperties.getTimeSyncMaxRetry() && Utils.listContainItem(respListParticipantXml.getTransaction().getResp().getRespCode(), Constants.TIME_SYNC_ERROR_CODE)) {
      log.info("Hitting sync time api ");
      timeService.syncTimeWithNetc();
      log.info("Time Sync api updated offset to {}", Utils.getNetcEngineClockOffset());
      respListParticipantXml = requestListParticipant(retryCount, msgId);
    }
  }
  
  public RespListParticipantXml requestListParticipant(int retryCount, String msgId) {
    String requestTimeStamp = Utils.getFormattedDate();
  
    ReqListParticipantXml request = ListParticipantAdapter.getListParticipantDto(requestTimeStamp, metadataService);
    request.setHeader(metadataService.createXmlHeader(request.getTransaction().getId(),
        requestTimeStamp, msgId ,retryCount));

    String signedXml = signatureService.signXmlDocument(XmlUtil.serializeXmlDocument(request));

    log.info("Signed request XML for List Participant :{}", signedXml);
    String response = wrappedNetcClient.requestListParticipant(signedXml);
    log.info("Signed response XML for List Participant :{}", response);
  
    RespListParticipantXml respListParticipantXml = XmlUtil.deserializeXmlDocument(response, RespListParticipantXml.class);
    populateDataInIINTableAndRedis(respListParticipantXml);
    return respListParticipantXml;
  }
  
  private void populateDataInIINTableAndRedis(RespListParticipantXml respListParticipantXml) {
    
    if(Objects.nonNull(respListParticipantXml) &&
        Objects.nonNull(respListParticipantXml.getTransaction()) &&
        Objects.nonNull(respListParticipantXml.getTransaction().getResp()) &&
        Objects.equals(respListParticipantXml.getTransaction().getResp().getResult(), RESULT_SUCCESS)) {
  
      validateService.validateListParticipantResponse(respListParticipantXml);
  
      List<IinInfo> iinInfoNewList = ListParticipantAdapter.iinInfoMapper(respListParticipantXml);
      Map<String, IinInfo> newIinInfoMap =
        iinInfoNewList.stream()
          .collect(Collectors.toMap(IinInfo::getShortCode, iinInfo -> iinInfo));
      
      List<IinInfo> finalList = getFinalIinList(newIinInfoMap);
      iinInfoMasterRepository.saveAll(finalList);
      
      redisService.saveIinParticipantList(Constants.IIN_PARTICIPANTS_KEY, iinInfoNewList);
    }
  }
  
  private List<IinInfo> getFinalIinList(Map<String, IinInfo> newIinInfoMap) {
  
    List<IinInfo> finalIinList = new ArrayList<>();
    List<IinInfo> iinInfoOldList = iinInfoSlaveRepository.findAll();
  
    Map<String, IinInfo> oldIinInfoMap =
      iinInfoOldList.stream()
        .collect(Collectors.toMap(IinInfo::getShortCode, iinInfo -> iinInfo));
    
    for (Map.Entry<String, IinInfo> entry : newIinInfoMap.entrySet()) {
      if(oldIinInfoMap.containsKey(entry.getKey())) { // for old participant which is also present in new data
        IinInfo iinInfoOld = oldIinInfoMap.get(entry.getKey());
        entry.getValue().setId(iinInfoOld.getId());
        entry.getValue().setCreatedAt(iinInfoOld.getCreatedAt());
        entry.getValue().setUpdatedAt(iinInfoOld.getUpdatedAt());
      }
      finalIinList.add(entry.getValue());
    }
  
    for (Map.Entry<String, IinInfo> entry : oldIinInfoMap.entrySet()) {
      if(!newIinInfoMap.containsKey(entry.getKey())) { // for old participant which is not present in new data
        log.info("Old entry having Id {} is not found in current data ", entry.getValue().getId());
        entry.getValue().setActive(false);
        finalIinList.add(entry.getValue());
      }
    }
    
    return finalIinList;
  }
  
  public RespListParticipant requestIssuerParticipant(List<String> issuerIins) {
    RespListParticipant respListParticipant = new RespListParticipant();
    List<IinInfo> iinInfoList;
    List<Participant> participantList = new ArrayList<>();
    int noOfInvalidIssuerParticipant = 0;
    
    iinInfoList = redisService.getListParticipantBank(Constants.IIN_PARTICIPANTS_KEY);
    
    if(iinInfoList.isEmpty()) {
      log.info("Did not found data in Redis, finding data from DB ");
      iinInfoList = findIinDataFromDB(issuerIins);
    }
  
    iinInfoList = iinInfoList.stream()
      .filter(iinInfo -> !(iinInfo.getIssuerIin().isBlank()))
      .collect(Collectors.toList());
  
    Map<String, IinInfo> iinInfoIssuerMap =
      iinInfoList.stream()
        .collect(Collectors.toMap(IinInfo::getIssuerIin, iinInfo -> iinInfo));
    
    if(issuerIins.get(0).equalsIgnoreCase(ALL_PARTICIPANT_CODE)) {
      
      for(IinInfo info : iinInfoList) {
        Participant participant = ListParticipantAdapter.getSuccessRespParticipantDto(info);
        participantList.add(participant);
      }
      respListParticipant.setNoOfParticipant(String.valueOf(iinInfoList.size()));
      respListParticipant.setResult(RESULT_SUCCESS);
      respListParticipant.setParticipantList(participantList);
      respListParticipant.setRespCode(SUCCESS_RESPONSE_CODE);
      
    } else {
      
      for(String issuerIin : issuerIins) {
        if(iinInfoIssuerMap.containsKey(issuerIin)) {
          Participant participant = ListParticipantAdapter.getSuccessRespParticipantDto(iinInfoIssuerMap.get(issuerIin));
          participantList.add(participant);
        } else {
          log.info("Did not found any entry for this issuerIin {}", issuerIin);
          Participant participant = ListParticipantAdapter.getFailureRespParticipantDto(issuerIin, PARTICIPANT_NOT_IN_DB_ERROR);
          participantList.add(participant);
          noOfInvalidIssuerParticipant ++;
        }
      }
      updateRespListParticipant(respListParticipant,issuerIins, participantList, noOfInvalidIssuerParticipant);
    
    }
    
    return respListParticipant;
  }
  
  private List<IinInfo> findIinDataFromDB(List<String> issuerIins) {
    if(issuerIins.get(0).equalsIgnoreCase(ALL_PARTICIPANT_CODE)) {
      return iinInfoSlaveRepository.findByIsActiveOrderByIdDesc(true);
    }
    return iinInfoSlaveRepository.findByIsActiveAndIssuerIinInOrderByIdDesc(true, issuerIins);
  }
  
  private void updateRespListParticipant(RespListParticipant respListParticipant, List<String> issuerIins, List<Participant> participantList, int noOfInvalidIssuerParticipant) {
    respListParticipant.setNoOfParticipant(String.valueOf(issuerIins.size()));
    respListParticipant.setParticipantList(participantList);
    if(Objects.equals(noOfInvalidIssuerParticipant, participantList.size())) {
      respListParticipant.setResult(RESULT_FAILURE);
      respListParticipant.setRespCode(PARTICIPANT_NOT_IN_DB_ERROR);
    }else if(noOfInvalidIssuerParticipant > 0) {
      respListParticipant.setResult(RESULT_PARTIAL);
      respListParticipant.setRespCode(SUCCESS_RESPONSE_CODE);
    }else {
      respListParticipant.setResult(RESULT_SUCCESS);
      respListParticipant.setRespCode(SUCCESS_RESPONSE_CODE);
    }
  }
}
