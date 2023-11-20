package com.paytm.acquirer.netc.adapter;

import com.paytm.acquirer.netc.db.entities.ErrorCodeMapping;
import com.paytm.acquirer.netc.db.repositories.master.ErrorCodeMappingMasterRepository;
import com.paytm.acquirer.netc.dto.common.VehicleDetails;
import com.paytm.acquirer.netc.dto.common.VehicleDetailsXml;
import com.paytm.acquirer.netc.dto.common.VehicleResponseXml;
import com.paytm.acquirer.netc.dto.common.VehicleTransactionXml;
import com.paytm.acquirer.netc.dto.details.ReqDetails;
import com.paytm.acquirer.netc.dto.details.ReqDetailsXml;
import com.paytm.acquirer.netc.dto.details.RespDetails;
import com.paytm.acquirer.netc.dto.details.RespDetailsXml;
import com.paytm.acquirer.netc.enums.TransactionType;
import com.paytm.acquirer.netc.service.common.MetadataService;
import com.paytm.acquirer.netc.util.Utils;
import lombok.experimental.UtilityClass;

import java.util.*;
import java.util.stream.Collectors;

import static com.paytm.acquirer.netc.enums.NetcEndpoint.REQ_DETAILS;
import static com.paytm.acquirer.netc.util.Constants.VehicleDetails.*;

@UtilityClass
public class DetailsAdapter {
    public static ReqDetailsXml convert(ReqDetails reqDetails, String timeStamp, MetadataService metadataService) {
        VehicleTransactionXml.VehicleXml vehicleXml = new VehicleTransactionXml.VehicleXml();
        vehicleXml.setTagId(reqDetails.getTagId());
        vehicleXml.setVehicleTId(reqDetails.getVehicleTId());
        vehicleXml.setVehicleRegNo(reqDetails.getVehicleRegNo());
        Utils.insertBlankForNullStings(vehicleXml);

        VehicleTransactionXml vehicleTransactionXml = new VehicleTransactionXml();
        vehicleTransactionXml.setVehicle(vehicleXml);
        vehicleTransactionXml.setType(TransactionType.FETCH);
        vehicleTransactionXml.setTimeStamp(timeStamp);
        metadataService.updateTransaction(vehicleTransactionXml, REQ_DETAILS);

        ReqDetailsXml reqDetailsXml = new ReqDetailsXml();
        reqDetailsXml.setTransaction(vehicleTransactionXml);
        Utils.insertBlankForNullStings(reqDetailsXml);

        return reqDetailsXml;
    }

    public static RespDetails convert(RespDetailsXml respDetailsXml, ErrorCodeMappingMasterRepository mappingRepository) {
        List<VehicleDetailsXml> vehicleDetailsXmls = Collections.emptyList();
        VehicleResponseXml.VehicleXml vehicle = respDetailsXml.getTransaction().getResponse().getVehicle();

        //check for null value.
        if (vehicle != null && vehicle.getVehicleDetailsList() != null) {
            vehicleDetailsXmls = vehicle.getVehicleDetailsList();
        }

        List<VehicleDetails> vehicleDetailsList = vehicleDetailsXmls.stream()
                .map(DetailsAdapter::getDetailsMap)
                .map(detailsMap -> {

                    VehicleDetails vehicleDetails = new VehicleDetails();
                    vehicleDetails.setBankId(detailsMap.get(BANK_ID));
                    vehicleDetails.setCommercialVehicle(detailsMap.get(COMMERCIAL_VEHICLE));
                    vehicleDetails.setExceptionCodes(Arrays.asList(detailsMap.get(EXEMPTION_CODE).split(",")));
                    vehicleDetails.setIssueDate(detailsMap.get(ISSUE_DATE));
                    vehicleDetails.setRegNumber(detailsMap.get(REG_NO));
                    vehicleDetails.setTagId(detailsMap.get(TAG_ID));
                    vehicleDetails.setVehicleClass(detailsMap.get(VEHICLE_CLASS));
                    vehicleDetails.setTid(detailsMap.get(TID));
                    vehicleDetails.setTagStatus(detailsMap.get(TAG_STATUS));

                    return vehicleDetails;
                }).collect(Collectors.toList());

        VehicleResponseXml responseXml = respDetailsXml.getTransaction().getResponse();
        RespDetails respDetails = new RespDetails();
        respDetails.setRespCode(responseXml.getResponseCode());
        if (responseXml.getVehicle() != null) {
            respDetails.setErrCode(responseXml.getVehicle().getErrorCode());
            Optional<ErrorCodeMapping> mapping = mappingRepository.findByErrorCode(respDetails.getErrCode());
            mapping.ifPresent(errorCodeMapping -> respDetails.setErrCodeMapping(errorCodeMapping.getMapping()));
        }
        respDetails.setResult(responseXml.getResult());

        respDetails.setVehicleDetails(vehicleDetailsList);
        return respDetails;
    }

    /**
     * Convert vehicle details in xml format to map of details
     *
     * @param detailsXml Vehicle details object containing list of details entities
     * @return Map of detail name to value
     */
    private static Map<String, String> getDetailsMap(VehicleDetailsXml detailsXml) {
        return detailsXml.getDetails().stream()
                .collect(Collectors.toMap(VehicleDetailsXml.Detail::getName, VehicleDetailsXml.Detail::getValue));
    }

}
