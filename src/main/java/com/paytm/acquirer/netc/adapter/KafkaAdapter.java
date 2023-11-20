package com.paytm.acquirer.netc.adapter;

import com.paytm.acquirer.netc.dto.pay.ReqPay;
import com.paytm.acquirer.netc.dto.pay.ReqPayXml;
import com.paytm.acquirer.netc.dto.pay.RespPayXml;
import com.paytm.acquirer.netc.dto.retry.RetryDto;
import com.paytm.acquirer.netc.enums.NetcEndpoint;
import com.paytm.acquirer.netc.enums.RetrialType;
import com.paytm.acquirer.netc.util.Constants;
import com.paytm.acquirer.netc.util.JsonUtil;
import lombok.experimental.UtilityClass;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.paytm.acquirer.netc.util.Constants.ReqPay.PAYEE_ADDR_SUFFIX;
import static com.paytm.acquirer.netc.util.Constants.RetryParams.*;

@UtilityClass
public class KafkaAdapter {
    public static RetryDto createKafkaMsgForReqPay(ReqPayXml request, ReqPay reqPay, RetrialType retrialType) {
        Map<String, String> map = new HashMap<>();
        map.put(Constants.RetryParams.ACQUIRER_ID, request.getPayee().getAddr().replace(PAYEE_ADDR_SUFFIX, ""));
        map.put(Constants.RetryParams.ISSUER_ID, request.getPayer().getAddr().split("@")[1].replace(".iin.npci", ""));
        map.put(Constants.RetryParams.TXN_DATE, request.getHeader().getTimeStamp().split("T")[0]);
        map.put(REF_ID, reqPay.getTxnReferenceId());
        map.put(Constants.REQ_PAY, JsonUtil.serialiseJson(reqPay));
        map.put(READER_READ_TIME,reqPay.getTagReadTime());

        RetryDto dto = new RetryDto();
        dto.setMsgId(request.getHeader().getMessageId());
        dto.setParams(map);
        dto.setApi(NetcEndpoint.RESP_PAY);
        dto.setRetrialType(retrialType);
        return dto;
    }

    public static RetryDto getExceptionKafkaMsg(String msgId, String msgNum, String totalMsg, String retryCount) {
        RetryDto dto = new RetryDto();

        Map<String, String> map = new HashMap<>();
        map.put(MSG_NUM, msgNum);
        map.put(TOTAL_MSG, totalMsg);
        map.put(RETRY_COUNT, retryCount);

        dto.setMsgId(msgId);
        dto.setParams(map);
        dto.setApi(NetcEndpoint.RESP_GET_EXCEPTION_LIST);

        return dto;
    }

    public static RetryDto queryExceptionKafkaMsg(
      String msgId, String msgNum, String totalMsg,
      String retryCount, String uid, boolean testRequestViaAPI) {
        RetryDto dto = new RetryDto();

        Map<String, String> map = new HashMap<>();
        map.put(MSG_NUM, msgNum);
        map.put(TOTAL_MSG, totalMsg);
        map.put(RETRY_COUNT, retryCount);
        map.put(Constants.UID, uid);
        map.put(Constants.TEST_REQUEST_VIA_API, Boolean.toString(testRequestViaAPI));

        dto.setParams(map);
        dto.setMsgId(msgId);
        dto.setApi(NetcEndpoint.RESP_QUERY_EXCEPTION_LIST);

        return dto;
    }

    public static RetryDto queryExceptionNewRequestKafkaMsg(String msgId, Integer retryCount,
                                                            String uid,String lastUpdatedTime) {
        RetryDto dto = new RetryDto();
        Map<String,String> params = new HashMap<>();
        params.put(RETRY_COUNT, String.valueOf(retryCount));
        params.put(Constants.UID,uid);
        params.put(Constants.LAST_UPDATED_TIME,lastUpdatedTime);
        dto.setMsgId(msgId);
        dto.setParams(params);
        dto.setApi(NetcEndpoint.QUERY_EXCEPTION_LIST);

        return dto;
    }

    public static RetryDto getExceptionNewRequestKafkaMsg(String msgId, Integer retryCount) {
        RetryDto dto = new RetryDto();
        dto.setMsgId(msgId);
        dto.setParams(Collections.singletonMap(RETRY_COUNT, String.valueOf(retryCount)));
        dto.setApi(NetcEndpoint.GET_EXCEPTION_LIST);

        return dto;
    }

    public static RetryDto createKafkaMsgForRetrialCodeReqPay(RespPayXml respPayXml, String refId) {
        Map<String, String> map = new HashMap<>();
    
        map.put(REF_ID, refId);
        map.put(NETC_TXN_ID, respPayXml.getRiskScoreTxn().getId());
    
        RetryDto dto = new RetryDto();
        dto.setMsgId(respPayXml.getHeader().getMessageId());
        dto.setParams(map);
        dto.setApi(NetcEndpoint.RESP_PAY);
        dto.setRetrialType(RetrialType.AUTO_RETRYABLE_CODE_RETRY);
        return dto;
    }
}
