package com.paytm.acquirer.netc.service;

import com.paytm.acquirer.netc.adapter.PayAdapter;
import com.paytm.acquirer.netc.dto.common.LaneXml;
import com.paytm.acquirer.netc.dto.common.MerchantXml;
import com.paytm.acquirer.netc.dto.pay.ReqPay;
import com.paytm.acquirer.netc.dto.pay.ReqPayXml;
import com.paytm.acquirer.netc.enums.PlazaCategory;
import com.paytm.acquirer.netc.service.common.MetadataService;
import com.paytm.acquirer.netc.util.Utils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReqPayTollService implements IReqPayService {
  @Override
  public ReqPayXml convertReqPayToXml(ReqPay reqPay, String requestTime, MetadataService metadataService) {
    ReqPayXml reqPayXml = PayAdapter.convertReqPayToXmlDto(reqPay, requestTime, metadataService);
    MerchantXml merchantXml = reqPayXml.getMerchant();
    merchantXml.setType(PlazaCategory.TOLL.name());

    LaneXml laneXml = new LaneXml();
    laneXml.setDirection(reqPay.getLaneDirection());
    laneXml.setId(reqPay.getLaneId());
    Utils.insertBlankForNullStings(laneXml);
    merchantXml.setLane(laneXml);

    return  reqPayXml;
  }
}
