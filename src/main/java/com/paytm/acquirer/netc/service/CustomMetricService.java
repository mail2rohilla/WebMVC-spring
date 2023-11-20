package com.paytm.acquirer.netc.service;

import com.paytm.acquirer.netc.db.entities.AsyncTransaction;
import com.paytm.acquirer.netc.db.repositories.master.AsyncTransactionMasterRepository;
import com.paytm.acquirer.netc.dto.queryException.RespQueryExceptionListXml;
import com.paytm.acquirer.netc.enums.NetcEndpoint;
import com.paytm.acquirer.netc.util.Constants;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import com.paytm.transport.metrics.DataDogClient;
import com.paytm.transport.metrics.Monitor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CustomMetricService {
    private static final Logger log = LoggerFactory.getLogger(CustomMetricService.class);

    private final DataDogClient dataDogClient;
    private final AsyncTransactionMasterRepository asyncTransactionMasterRepository;

    public void recordElapsedTimeExecutionMetric(Monitor.ServiceGroup serviceGroup, long startTime, String apiName) {
        Map<String, String> map = new LinkedHashMap<>();
        long elapsedTime = System.currentTimeMillis() - startTime;
        map.put(Constants.MetricConstants.ACQUIRER_ENTITY_TAG, apiName);
        dataDogClient.recordExecutionMetric(serviceGroup, elapsedTime, map);
    }

    public void recordCountMetric(Monitor.ServiceGroup serviceGroup, String apiName, String status) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(Constants.MetricConstants.ACQUIRER_ENTITY_TAG, apiName);
        map.put(Constants.MetricConstants.STATUS, status);
        dataDogClient.incrementCounter(serviceGroup,map);
    }
    
    public void recordMetricForCircuitBreaker(Monitor.ServiceGroup serviceGroup, String toState) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(Constants.MetricConstants.ACQUIRER_ENTITY_TAG, Constants.MetricConstants.NETC_CIRCUIT_BREKER);
        map.put(Constants.MetricConstants.TRANSITION, toState);
        dataDogClient.incrementCounter(serviceGroup,map);
    }

    public void recordMetricForUnknownMessageId(String metricType) {
        Map<String, String> tagsMap = new HashMap<>();
        tagsMap.put(Constants.MetricConstants.ACQUIRER_ENTITY_TAG,
          Constants.MetricConstants.ACQUIRER_ENTITIES.UNKNOWN_MSG_ID.name());
        tagsMap.put(Constants.MetricConstants.ENTITY_TYPE, metricType);
        dataDogClient.incrementCounter(Monitor.ServiceGroup.COUNTER, tagsMap);
    }

    public void recordElapsedTimeForDiffResp(AsyncTransaction request, RespQueryExceptionListXml responseData) {
        try {
            long startTime = request.getCreatedAt().getTime();
            String metricType = "INITIAL_RESPONSE";
            if(responseData.getTransaction().getResponse().getMessageNumber() > 1) {

                Optional<AsyncTransaction> previousResp = asyncTransactionMasterRepository.findByMsgIdAndApiAndMsgNum(
                  responseData.getHeader().getMessageId(),
                  NetcEndpoint.RESP_QUERY_EXCEPTION_LIST,
                  responseData.getTransaction().getResponse().getMessageNumber() - 1);

                if(!previousResp.isPresent()) {
                    log.error("Previous msgNum doesnt exist for this messageId: {}",
                      responseData.getHeader().getMessageId());
                    return;
                }
                startTime = previousResp.get().getCreatedAt().getTime();
                metricType = "CONSECUTIVE_RESPONSE";
            }

            long elapsedTime = System.currentTimeMillis() - startTime;
            Map<String, String> tagsMap = new HashMap<>();
            tagsMap.put(Constants.MetricConstants.ACQUIRER_ENTITY_TAG,
              Constants.MetricConstants.ACQUIRER_ENTITIES.DIFF_RESP_TIME_TAKEN.name());
            tagsMap.put(Constants.MetricConstants.ENTITY_TYPE, metricType);
            dataDogClient.recordExecutionMetric(Monitor.ServiceGroup.TIME_TAKEN,
              elapsedTime, tagsMap);
        }
        catch(Exception e) {
            log.error("Error while sending DIFF response metrics ", e);
        }
    }


    public void recordCompleteDiffLog(String msgId, boolean isDiffViaSftp, Long totalTags, boolean isEmptyDiff,
      LocalDateTime startTime, Integer totalFiles) {
        Map<String, String> map = new LinkedHashMap<>();
        Duration duration = Duration.between(startTime, LocalDateTime.now());
        long elapsedTime = duration.toMillis();
        map.put(Constants.MetricConstants.ACQUIRER_ENTITY_TAG,
          Constants.MetricConstants.ACQUIRER_ENTITIES.DIFF_MSG_DETAIL.name());
        map.put(Constants.MetricConstants.ENTITY_TYPE, isDiffViaSftp ? "SFTP": "API");
        map.put(Constants.MetricConstants.MSG_ID, msgId);
        map.put(Constants.MetricConstants.STATUS, isEmptyDiff ? Constants.RESULT_FAILURE: Constants.RESULT_SUCCESS);
        map.put(Constants.MetricConstants.TOTAL_TAGS, totalTags.toString());
        map.put(Constants.MetricConstants.TIME_TAKEN, String.valueOf(duration.getSeconds()));
        map.put(Constants.MetricConstants.START_TIME, startTime.toString());
        map.put(Constants.MetricConstants.TOTAL_FILES, totalFiles.toString());
        dataDogClient.recordExecutionMetric(Monitor.ServiceGroup.TIME_TAKEN, elapsedTime, map);
    }
}
