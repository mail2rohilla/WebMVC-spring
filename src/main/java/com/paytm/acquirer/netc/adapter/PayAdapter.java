package com.paytm.acquirer.netc.adapter;

import com.paytm.acquirer.netc.dto.common.*;
import com.paytm.acquirer.netc.dto.pay.ReqPay;
import com.paytm.acquirer.netc.dto.pay.ReqPayXml;
import com.paytm.acquirer.netc.dto.pay.RespPay;
import com.paytm.acquirer.netc.dto.pay.RespPayXml;
import com.paytm.acquirer.netc.enums.Status;
import com.paytm.acquirer.netc.service.common.MetadataService;
import com.paytm.acquirer.netc.util.Utils;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;

import javax.validation.constraints.Size;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.*;

import static com.paytm.acquirer.netc.util.Constants.ReqPay.*;
import static com.paytm.acquirer.netc.util.Constants.VehicleDetails.*;
import static com.paytm.acquirer.netc.util.Utils.*;

@UtilityClass
public class PayAdapter {
  private static final Logger log = LoggerFactory.getLogger(PayAdapter.class);

  public static ReqPayXml convertReqPayToXmlDto(ReqPay reqPay, String timeStamp, MetadataService metadataService) {
    if (reqPay == null) {
      return null;
    }

    // create Risk Score
    RiskScoreXml riskScoreXml = new RiskScoreXml();
    Utils.insertBlankForNullStings(riskScoreXml);

    // create Txn
    RiskScoreTxnXml riskScoreTxnXml = new RiskScoreTxnXml();
    riskScoreTxnXml.setReferenceId(reqPay.getPlazaTxnId());
    riskScoreTxnXml.setTimeStamp(reqPay.getTxnTime());
    riskScoreTxnXml.setType(reqPay.getTxnType());
    riskScoreTxnXml.setRiskScores(Collections.singletonList(riskScoreXml));
    riskScoreTxnXml.setNote(reqPay.getNote());
    metadataService.updateTransactionReqPay(reqPay.getNetcTxnId(), riskScoreTxnXml);

    // create vehicle
    ReqPayXml.VehicleXml vehicleXml = new ReqPayXml.VehicleXml();
    vehicleXml.setVehicleTId(reqPay.getVehicleDetails() != null ? reqPay.getVehicleDetails().getTid() : "");
    vehicleXml.setTagId(reqPay.getTagId());
    vehicleXml.setVehicleClassByAvc(reqPay.getAvc());
    vehicleXml.setVehicleWeight(Strings.isNotBlank(reqPay.getWim()) ? reqPay.getWim().toUpperCase() : reqPay.getWim());
    Utils.insertBlankForNullStings(vehicleXml);

    // create vehicle Detail
    VehicleDetailsXml vehicleDetailsXml = new VehicleDetailsXml();
    Set<VehicleDetailsXml.Detail> vehicleDetails = new LinkedHashSet<>();
    vehicleDetailsXml.setDetails(vehicleDetails);
    vehicleXml.setVehicleDetailsList(Collections.singletonList(vehicleDetailsXml));
    Utils.insertBlankForNullStings(vehicleDetailsXml);

    VehicleDetails details = reqPay.getVehicleDetails();
    vehicleDetails.add(new VehicleDetailsXml.Detail(VEHICLE_CLASS, details.getVehicleClass()));
    vehicleDetails.add(new VehicleDetailsXml.Detail(REG_NO, details.getRegNumber()));
    vehicleDetails.add(new VehicleDetailsXml.Detail(TAG_STATUS, details.getTagStatus()));
    vehicleDetails.add(new VehicleDetailsXml.Detail(ISSUE_DATE, transformDate(details.getIssueDate())));
    vehicleDetails.add(new VehicleDetailsXml.Detail(EXEMPTION_CODE, String.join(",", details.getExceptionCodes())));
    vehicleDetails.add(new VehicleDetailsXml.Detail(BANK_ID, details.getBankId()));
    vehicleDetails.add(new VehicleDetailsXml.Detail(COMMERCIAL_VEHICLE, details.getCommercialVehicle()));

    // create Lane
    LaneXml laneXml = new LaneXml();
    Utils.insertBlankForNullStings(laneXml);

    // create Verification data
    ReaderVerificationResultXml verificationResultXml = new ReaderVerificationResultXml();
    verificationResultXml.setTsRead(reqPay.getTagReadTime());
    verificationResultXml.setSignAuth(SIGN_AUTH);
    verificationResultXml.setTagVerified(TAG_VERIFIED);
    verificationResultXml.setVehicleAuth(VEHICLE_AUTH);
    verificationResultXml.setTxnCounter(metadataService.getTxnCounter(reqPay.getPlazaId()));
    verificationResultXml.setTxnStatus(
      !StringUtils.isEmpty(reqPay.getTxnStatus()) ? reqPay.getTxnStatus() : TXN_STATUS);
    Utils.insertBlankForNullStings(verificationResultXml);

    ParkingXml parkingXml = new ParkingXml();
    Utils.insertBlankForNullStings(parkingXml);

    // create merchant
    MerchantXml merchantXml = new MerchantXml();
    merchantXml.setGeoCode(reqPay.getPlazaGeoCode());
    merchantXml.setId(reqPay.getPlazaId());
    merchantXml.setSubtype(reqPay.getPlazaType());
    merchantXml.setLane(laneXml);
    merchantXml.setVerificationResult(verificationResultXml);
    merchantXml.setParking(parkingXml);
    Utils.insertBlankForNullStings(merchantXml);

    // create Payee
    PaymentXml payeeXml = new PaymentXml();
    payeeXml.setType(PAYEE_TYPE);
    payeeXml.setAddr(reqPay.getAcquirerId() + PAYEE_ADDR_SUFFIX);
    Utils.insertBlankForNullStings(payeeXml);

    // create Payer Amount
    AmountXml payerAmt = new AmountXml();
    payerAmt.setCurr(CURRENCY);
    payerAmt.setValue(reqPay.getAmount());
    Utils.insertBlankForNullStings(payerAmt);
    // create Payer
    PaymentXml payerXml = new PaymentXml();
    payerXml.setName(reqPay.getBbpsTransactionId());
    payerXml.setType(PAYER_TYPE);
    payerXml.setAddr(getPayerAddr(reqPay.getTagId(), reqPay.getBankId()));
    payerXml.setAmount(payerAmt);
    Utils.insertBlankForNullStings(payerXml);

    // create meta tags
    List<MetaTagXml> metaTagXmls = new ArrayList<>();
    metaTagXmls.add(new MetaTagXml(PAYMENT_REQ_START, timeStamp));
    metaTagXmls.add(new MetaTagXml(PAYMENT_REQ_END, timeStamp));
    metaTagXmls.add(new MetaTagXml(STATIC_WEIGHT, ""));

    // create final XML
    ReqPayXml reqPayXml = new ReqPayXml();
    reqPayXml.setTransaction(riskScoreTxnXml);
    reqPayXml.setVehicle(vehicleXml);
    reqPayXml.setMerchant(merchantXml);
    reqPayXml.setPayee(payeeXml);
    reqPayXml.setPayer(payerXml);
    reqPayXml.setMetaTags(metaTagXmls);
    Utils.insertBlankForNullStings(reqPayXml);

    return reqPayXml;
  }

  /**
   * Generates Payer address from tagId
   * Note: assumption tagId length is 24 chars
   *
   * @param tagId Tag ID in hexadecimal format (12 bytes/24 chars)
   * @param bankId Issuer Bank Id
   * @return Payer address in format <TagID>@<IssuerID>.iin.npci
   */
  private static String getPayerAddr(@Size(max = 24) String tagId, String bankId) {
    return String.format(PAYER_ADDR_FMT, tagId, bankId);
  }

  public static String issuerId(String tagId) {
    //this is done as per discussion with vikram
    if (tagId.startsWith("918907048")) {
      return issuerIdForIciciTag();
    } else {
      return issuerIdNormalTag(tagId);
    }
  }

  private static String issuerIdForIciciTag() {
    log.info("this is icici tag, so returning hardcoded issuer id.");
    return "607417";
  }

  /**
   * TPT-4775: Issuer Id extraction as per NPCI PG document
   * <br>
   * Note: Don't use this for IHMCL Tags (Bank Neutral Tags)
   * @param tagId Tag ID in hexadecimal format (12 bytes/24 chars)
   * @return Issuer bankId
   */
  private static String issuerIdNormalTag(String tagId) {
    String binaryTagId = new BigInteger(tagId, 16).toString(2);
    // prepend zeros
    binaryTagId = String.format("%96s", binaryTagId).replace(' ', '0');
    String binaryIssuerId = binaryTagId.substring(43, 63);
    return new BigInteger(binaryIssuerId, 2).toString(10);
  }

  public static RespPay respPayXmlToRespPayKafka(RespPayXml xml, String refId) {
    RespPay respPay = new RespPay();
    respPay.setRefId(refId);
    respPay.setStatus(Status.RESPONSE_RECEIVED);
    respPay.setResult(xml.getResponse().getResult());
    respPay.setNetcTxnId(xml.getRiskScoreTxn().getId());
    //TPT-5147: use system time to represent NPCI response time
    //TPT-5971: adding 1 second to local time since the milliseconds get dropped while formatting
    respPay.setNetcResponseTime(LocalDateTime.now().plusSeconds(1).format(dateTimeFormatter));
    respPay.setErrorCodes(xml.getResponse().getResponseCode());

    return respPay;
  }

  public static VehicleDetails createVehicleDetailsForUnRegisteredTag(ReqPay reqPay) {
    VehicleDetails details = new VehicleDetails();

    details.setTid(reqPay.getTagId());
    details.setTagId(reqPay.getTagId());

    details.setVehicleClass(reqPay.getAvc());
    details.setRegNumber("NA");
    details.setTagStatus("");
    details.setIssueDate(getTodayDate());
    details.setExceptionCodes(Collections.singletonList("00"));
    details.setBankId(reqPay.getBankId());
    details.setCommercialVehicle("F");
    return details;
  }
}
