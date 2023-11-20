package com.paytm.acquirer.netc.service;

import com.paytm.acquirer.netc.dto.pay.ReqPay;
import com.paytm.acquirer.netc.dto.pay.ReqPayXml;
import com.paytm.acquirer.netc.service.common.MetadataService;
import org.springframework.stereotype.Service;

@Service
public interface IReqPayService {

  ReqPayXml convertReqPayToXml(ReqPay reqPay, String requestTime, MetadataService metadataService);

}
