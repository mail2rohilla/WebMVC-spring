package com.paytm.acquirer.netc.service;

import com.paytm.acquirer.netc.adapter.PayAdapter;
import com.paytm.acquirer.netc.dto.common.MerchantXml;
import com.paytm.acquirer.netc.dto.common.ParkingXml;
import com.paytm.acquirer.netc.dto.pay.ReqPay;
import com.paytm.acquirer.netc.dto.pay.ReqPayXml;
import com.paytm.acquirer.netc.enums.PlazaCategory;
import com.paytm.acquirer.netc.service.common.MetadataService;
import com.paytm.acquirer.netc.util.Utils;
import org.springframework.stereotype.Service;

@Service
public class ReqPayParkingService implements IReqPayService {

  @Override
  public ReqPayXml convertReqPayToXml(ReqPay reqPay, String requestTime, MetadataService metadataService) {
    ReqPayXml reqPayXml = PayAdapter.convertReqPayToXmlDto(reqPay, requestTime, metadataService);

    MerchantXml merchantXml = reqPayXml.getMerchant();
    merchantXml.setType(PlazaCategory.PARKING.name());

    ParkingXml parkingXml = merchantXml.getParking();
    parkingXml.setFloor(reqPay.getFloor());
    parkingXml.setReaderId(reqPay.getReaderId());
    parkingXml.setSlotId(reqPay.getSlotId());
    parkingXml.setZone(reqPay.getZone());

    Utils.insertBlankForNullStings(parkingXml);

    return  reqPayXml;
  }
}
