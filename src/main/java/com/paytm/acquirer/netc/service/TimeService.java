package com.paytm.acquirer.netc.service;

import com.paytm.acquirer.netc.dto.syncTime.ReqSyncTimeXml;
import com.paytm.acquirer.netc.dto.syncTime.RespSyncTimeXml;
import com.paytm.acquirer.netc.dto.syncTime.TimeSyncResponse;
import com.paytm.acquirer.netc.service.common.MetadataService;
import com.paytm.acquirer.netc.service.common.SignatureService;
import com.paytm.acquirer.netc.service.retry.WrappedNetcClient;
import com.paytm.acquirer.netc.util.JsonUtil;
import com.paytm.acquirer.netc.util.Utils;
import com.paytm.acquirer.netc.util.XmlUtil;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import com.paytm.transport.metrics.DataDogClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TimeService {
  private static final Logger log = LoggerFactory.getLogger(TimeService.class);

  private final SignatureService signatureService;
  private final MetadataService metadataService;
  private final WrappedNetcClient retryWrapperService;
  private final DataDogClient dataDogClient;

  public TimeSyncResponse syncTime() {
    // create common timeStamp
    String requestTimeStamp = Utils.getFormattedDate();

    ReqSyncTimeXml reqSyncTimeXml = new ReqSyncTimeXml();
    reqSyncTimeXml.setHeader(metadataService.createXmlHeader(null, requestTimeStamp, null));
    log.debug("reqSyncTimeXml : {}", reqSyncTimeXml.toString());

    String signedXml = signatureService.signXmlDocument(XmlUtil.serializeXmlDocument(reqSyncTimeXml));

    log.debug("Signed Request XML for ReqSyncTime : {}", signedXml);
    String tasLocalServerTime = LocalDateTime.now().format(Utils.dateTimeFormatter);
    String response = retryWrapperService.requestSyncTime(signedXml);
    Assert.notNull(response, "TimeSync api response came with empty body");
    log.debug("Signed Response XML for ReqSyncTime : {}", response);

    RespSyncTimeXml respSyncTimeXml = XmlUtil.deserializeXmlDocument(response, RespSyncTimeXml.class);
    log.debug("Response from NPCI : ", respSyncTimeXml.toString());

    String serverTime = respSyncTimeXml.getResponseXml().getNetcTime().getServerTime();
    String localTime = respSyncTimeXml.getHeader().getTimeStamp();
    TimeSyncResponse resp = new TimeSyncResponse(localTime, serverTime,tasLocalServerTime);
    log.info("Response for ReqSyncTime : {}", JsonUtil.serialiseJson(resp));

    return resp;
  }

  public void syncTimeWithNetc() {

    TimeSyncResponse response = syncTime();
    log.info("Time Sync response",JsonUtil.serialiseJson(response));
    Utils.adjustClockOffset(response,dataDogClient);
  }
}
