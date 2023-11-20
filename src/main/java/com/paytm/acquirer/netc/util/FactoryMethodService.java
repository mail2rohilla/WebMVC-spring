package com.paytm.acquirer.netc.util;

import com.paytm.acquirer.netc.dto.pay.ReqPay;
import com.paytm.acquirer.netc.service.IReqPayService;
import com.paytm.acquirer.netc.service.ReqPayParkingService;
import com.paytm.acquirer.netc.service.ReqPayTollService;
import com.paytm.acquirer.netc.service.common.EfkonSignatureService;
import com.paytm.acquirer.netc.service.common.ISignatureService;
import com.paytm.acquirer.netc.service.common.SignatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static com.paytm.acquirer.netc.enums.PlazaCategory.PARKING;

@Service
@RequiredArgsConstructor
public class FactoryMethodService {

  private final ApplicationContext context;

  public IReqPayService getInstance(ReqPay reqPay){
    if(Objects.equals(reqPay.getPlazaCategory(), PARKING)) {
      return context.getBean(ReqPayParkingService.class);
    }
    return context.getBean(ReqPayTollService.class);
  }

  public ISignatureService getInstance(String orgId) {
    if(Objects.equals(orgId, "EFKON")) {
      return context.getBean(EfkonSignatureService.class);
    }
    return context.getBean(SignatureService.class);
  }
}
