package com.paytm.acquirer.netc.adapter;

import com.paytm.acquirer.netc.db.entities.AsyncTransaction;
import com.paytm.acquirer.netc.dto.common.QueryExceptionTransactionXml;
import com.paytm.acquirer.netc.dto.getException.ReqGetExceptionListXml;
import com.paytm.acquirer.netc.dto.getException.ReqGetExceptionListXml.GetExceptionTransactionXml;
import com.paytm.acquirer.netc.dto.getException.RespGetExceptionListXml;
import com.paytm.acquirer.netc.dto.manageException.ReqMngExceptionDto;
import com.paytm.acquirer.netc.dto.manageException.ReqMngExceptionXml;
import com.paytm.acquirer.netc.dto.manageException.RespMngExceptionDto;
import com.paytm.acquirer.netc.dto.manageException.RespMngExceptionXml;
import com.paytm.acquirer.netc.dto.pay.ReqPay;
import com.paytm.acquirer.netc.dto.queryException.ReqQueryExceptionListXml;
import com.paytm.acquirer.netc.dto.queryException.RespQueryExceptionListXml;
import com.paytm.acquirer.netc.enums.ExceptionCode;
import com.paytm.acquirer.netc.enums.NetcEndpoint;
import com.paytm.acquirer.netc.enums.Status;
import com.paytm.acquirer.netc.enums.TransactionType;
import com.paytm.acquirer.netc.service.common.MetadataService;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.paytm.acquirer.netc.enums.ExceptionCode.*;

@UtilityClass
public class ExceptionAdapter {
    public static ReqGetExceptionListXml getExceptionListDto(String timestamp, MetadataService metadataService) {
        // create exception list
        List<ReqGetExceptionListXml.Exceptions> exceptions =
          List.of(
            new ReqGetExceptionListXml.Exceptions(HOTLIST.toString()),
            new ReqGetExceptionListXml.Exceptions(EXEMPTED_VEHICLE_CLASS.toString()),
            new ReqGetExceptionListXml.Exceptions(LOW_BALANCE.toString()),
            new ReqGetExceptionListXml.Exceptions(INVALID_CARRIAGE.toString()),
            new ReqGetExceptionListXml.Exceptions(BLACKLIST.toString()),
            new ReqGetExceptionListXml.Exceptions(CLOSED_REPLACED.toString())
          );

        // create Txn
        GetExceptionTransactionXml transactionXml = new GetExceptionTransactionXml();
        transactionXml.setExceptionList(exceptions);
        transactionXml.setType(TransactionType.FETCHEXCEPTION);
        transactionXml.setTimeStamp(timestamp);
        metadataService.updateTransaction(transactionXml, NetcEndpoint.GET_EXCEPTION_LIST);

        // create request XML
        ReqGetExceptionListXml request = new ReqGetExceptionListXml();
        request.setTransaction(transactionXml);

        return request;
    }

    public static ReqQueryExceptionListXml queryExceptionListDto(String timestamp, String lastFetchTime,
                                                                 MetadataService metadataService) {
        // create exception list
        List<QueryExceptionTransactionXml.Exceptions> exceptions =
                List.of(
                  new QueryExceptionTransactionXml.Exceptions(HOTLIST.toString(), lastFetchTime),
                  new QueryExceptionTransactionXml.Exceptions(EXEMPTED_VEHICLE_CLASS.toString(), lastFetchTime),
                  new QueryExceptionTransactionXml.Exceptions(LOW_BALANCE.toString(), lastFetchTime),
                  new QueryExceptionTransactionXml.Exceptions(INVALID_CARRIAGE.toString(), lastFetchTime),
                  new QueryExceptionTransactionXml.Exceptions(BLACKLIST.toString(), lastFetchTime),
                  new QueryExceptionTransactionXml.Exceptions(CLOSED_REPLACED.toString(), lastFetchTime)
                );
        // create Txn
        QueryExceptionTransactionXml transactionXml = new QueryExceptionTransactionXml();
        transactionXml.setExceptionList(exceptions);
        transactionXml.setType(TransactionType.Query);
        transactionXml.setTimeStamp(timestamp);
        metadataService.updateTransaction(transactionXml, NetcEndpoint.QUERY_EXCEPTION_LIST);

        // create request XML
        ReqQueryExceptionListXml request = new ReqQueryExceptionListXml();
        request.setTransaction(transactionXml);

        return request;
    }

    public static ReqMngExceptionXml convert(ReqMngExceptionDto reqMngExceptionDto, String timeStamp, MetadataService metadataService) {
        // create tags list
        int seqNum = 1;
        List<ReqMngExceptionXml.Tag> tags = new ArrayList<>();
        for (ReqMngExceptionDto.TagEntry tagEntry : reqMngExceptionDto.getTagEntryTagList()) {
            tags.add(new ReqMngExceptionXml.Tag(tagEntry.getOperation(), tagEntry.getTagId(),
                    seqNum++, tagEntry.getExceptionCode()));
        }

        // create txn
        ReqMngExceptionXml.MngExceptionTransactionXml transactionXml =
                new ReqMngExceptionXml.MngExceptionTransactionXml();
        transactionXml.setType(TransactionType.ManageException);
        transactionXml.setTimeStamp(timeStamp);
        transactionXml.setTagList(tags);
        metadataService.updateTransaction(transactionXml, NetcEndpoint.MNG_EXCEPTION);


        // create request
        ReqMngExceptionXml reqMngExceptionXml = new ReqMngExceptionXml();
        reqMngExceptionXml.setTransaction(transactionXml);
        return reqMngExceptionXml;
    }

    public static RespMngExceptionDto convert(RespMngExceptionXml xml, Map<String, String> errorCodeMapping) {
        // create tag entries
        List<RespMngExceptionDto.TagEntry> tagEntries = new ArrayList<>();
        if (xml.getTransaction().getResponse().getTags() != null) {
            tagEntries = xml.getTransaction().getResponse().getTags().stream()
                    .map(tag -> new RespMngExceptionDto.TagEntry(tag.getOperation(), tag.getTagId(), tag.getResult(),
                            tag.getErrorCode(), errorCodeMapping.get(tag.getErrorCode())))
                    .collect(Collectors.toList());
        }

        // create response
        RespMngExceptionDto respMngExceptionDto = new RespMngExceptionDto();
        respMngExceptionDto.setRespCode(xml.getTransaction().getResponse().getResponseCode());
        respMngExceptionDto.setResult(xml.getTransaction().getResponse().getResult());
        respMngExceptionDto.setSuccessRequestCount(xml.getTransaction().getResponse().getSuccessRequestCount());
        respMngExceptionDto.setTotalRequestCount(xml.getTransaction().getResponse().getTotalRequestCount());
        respMngExceptionDto.setTagEntries(tagEntries);
        return respMngExceptionDto;
    }

    public static AsyncTransaction getAsyncTxnFromXml(RespGetExceptionListXml data) {
        AsyncTransaction transaction = new AsyncTransaction();
        transaction.setMsgId(data.getHeader().getMessageId());
        transaction.setTxnId(data.getTransaction().getId());
        transaction.setStatus(Status.RESPONSE_RECEIVED);
        transaction.setApi(NetcEndpoint.RESP_GET_EXCEPTION_LIST);
        transaction.setStatusCode(data.getTransaction().getResponse().getResponseCode());
        transaction.setMsgNum(data.getTransaction().getResponse().getMessageNumber());
        transaction.setTotalMsg(data.getTransaction().getResponse().getTotalMessage());
        return transaction;
    }

    public static AsyncTransaction getAsyncTxnFromXml(RespQueryExceptionListXml data) {
        AsyncTransaction transaction = new AsyncTransaction();
        transaction.setMsgId(data.getHeader().getMessageId());
        transaction.setTxnId(data.getTransaction().getId());
        transaction.setStatus(Status.RESPONSE_RECEIVED);
        transaction.setApi(NetcEndpoint.RESP_QUERY_EXCEPTION_LIST);
        transaction.setStatusCode(data.getTransaction().getResponse().getResponseCode());
        transaction.setMsgNum(data.getTransaction().getResponse().getMessageNumber());
        transaction.setTotalMsg(data.getTransaction().getResponse().getTotalMessage());
        return transaction;
    }

    public static ReqMngExceptionDto createBlackListDto(ReqPay reqPay) {
        ReqMngExceptionDto.TagEntry blackListEntry = new ReqMngExceptionDto.TagEntry();
        blackListEntry.setExceptionCode(ExceptionCode.HOTLIST.getValue());
        blackListEntry.setOperation("ADD");
        blackListEntry.setTagId(reqPay.getVehicleDetails().getTagId());

        ReqMngExceptionDto reqMngExceptionDto = new ReqMngExceptionDto();
        reqMngExceptionDto.setTagEntryTagList(Collections.singletonList(blackListEntry));

        return reqMngExceptionDto;
    }
}
