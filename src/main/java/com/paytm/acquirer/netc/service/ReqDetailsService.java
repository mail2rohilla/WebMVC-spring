package com.paytm.acquirer.netc.service;

import com.paytm.acquirer.netc.adapter.DetailsAdapter;
import com.paytm.acquirer.netc.config.properties.RetryProperties;
import com.paytm.acquirer.netc.db.repositories.master.ErrorCodeMappingMasterRepository;
import com.paytm.acquirer.netc.dto.common.VehicleDetails;
import com.paytm.acquirer.netc.dto.details.ReqDetails;
import com.paytm.acquirer.netc.dto.details.ReqDetailsXml;
import com.paytm.acquirer.netc.dto.details.RespDetails;
import com.paytm.acquirer.netc.dto.details.RespDetailsXml;
import com.paytm.acquirer.netc.service.common.MetadataService;
import com.paytm.acquirer.netc.service.common.SignatureService;
import com.paytm.acquirer.netc.service.retry.WrappedNetcClient;
import com.paytm.acquirer.netc.util.Constants;
import com.paytm.acquirer.netc.util.Utils;
import com.paytm.acquirer.netc.util.XmlUtil;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;

@Service
@AllArgsConstructor
public class ReqDetailsService {
  private static final Logger log = LoggerFactory.getLogger(ReqDetailsService.class);

  private final SignatureService signatureService;
  private final MetadataService metadataService;
  private final ErrorCodeMappingMasterRepository mappingRepository;
  private final TimeService timeService;
  private final RetryProperties retryProperties;
  private final WrappedNetcClient wrappedNetcClient;

  private RespDetails requestDetails(ReqDetails reqDetails, String msgId) {
    // create common time to be used across a request
    String requestTime = Utils.getFormattedDate();

    ReqDetailsXml request = DetailsAdapter.convert(reqDetails, requestTime, metadataService);
    request.setHeader(metadataService.createXmlHeader(request.getTransaction().getId(), requestTime, msgId));

    String signedXml = signatureService.signXmlDocument(XmlUtil.serializeXmlDocument(request));

    log.info("Signed Request XML for ReqDetails : {}", signedXml);
    String response = wrappedNetcClient.requestDetails(signedXml);
    log.info("Signed Response XML for ReqDetails : {}", response);

    RespDetailsXml respDetailsXml = XmlUtil.deserializeXmlDocument(response, RespDetailsXml.class);
    return DetailsAdapter.convert(respDetailsXml, mappingRepository);
  }

  public RespDetails requestDetailsWithRetry(ReqDetails reqDetails, String msgId) {
    RespDetails respDetails = requestDetails(reqDetails, msgId);
    log.info("Time sync server offset {} local server time {}",Utils.getNetcEngineClockOffset(), LocalDateTime.now());
    int retryCount = 0;
    while (retryCount++ < retryProperties.getTimeSyncMaxRetry()
        && Utils.listContainItem(respDetails.getRespCode(), Constants.TIME_SYNC_ERROR_CODE)) {
    log.info("Hitting sync time api for tag id {}", reqDetails.getTagId());
    timeService.syncTimeWithNetc();
    log.info("Time Sync api updated offset to {}",Utils.getNetcEngineClockOffset());
    respDetails = requestDetails(reqDetails, msgId);
    }
    //sort in tag issuance decending order
    respDetails
      .getVehicleDetails()
      .sort(Comparator.comparing(VehicleDetails::getIssueDate).reversed());
    return respDetails;
  }
}
