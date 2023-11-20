package com.paytm.acquirer.netc.dto.pay;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.paytm.acquirer.netc.dto.common.BaseXml;
import com.paytm.acquirer.netc.dto.common.MerchantXml;
import com.paytm.acquirer.netc.dto.common.MetaTagXml;
import com.paytm.acquirer.netc.dto.common.PaymentXml;
import com.paytm.acquirer.netc.dto.common.RiskScoreTxnXml;
import com.paytm.acquirer.netc.dto.common.VehicleDetailsXml;
import lombok.Data;

import java.util.List;

/**
 * Request Pay XML DTO
 */
@Data
@JacksonXmlRootElement(localName = "etc:ReqPay")
public class ReqPayXml extends BaseXml {

    @JacksonXmlElementWrapper(localName = "Meta")
    @JacksonXmlProperty(localName = "Tag")
    private List<MetaTagXml> metaTags;

    @JacksonXmlProperty(localName = "Txn")
    private RiskScoreTxnXml transaction;

    @JacksonXmlProperty(localName = "Merchant")
    private MerchantXml merchant;

    @JacksonXmlProperty(localName = "Vehicle")
    private VehicleXml vehicle;

    @JacksonXmlProperty(localName = "Payer")
    private PaymentXml payer;

    @JacksonXmlProperty(localName = "Payee")
    private PaymentXml payee;

    @Data
    public static class VehicleXml {
        @JacksonXmlProperty(isAttribute = true)
        private String tagId;

        @JacksonXmlProperty(isAttribute = true, localName = "TID")
        private String vehicleTId;

        /**
         * Vehicle class captured by the AVC machine at plaza
         */
        @JacksonXmlProperty(isAttribute = true, localName = "avc")
        private String vehicleClassByAvc;

        @JacksonXmlProperty(isAttribute = true, localName = "wim")
        private String vehicleWeight;

        @JacksonXmlProperty(localName = "VehicleDetails")
        @JacksonXmlElementWrapper(useWrapping = false)
        private List<VehicleDetailsXml> vehicleDetailsList;
    }
}
