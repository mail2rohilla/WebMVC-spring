package com.paytm.acquirer.netc.service.common;

import com.paytm.acquirer.netc.config.properties.RedisTtlConfig;
import com.paytm.acquirer.netc.db.entities.IinInfo;
import com.paytm.acquirer.netc.dto.queryException.RedisTag;
import com.paytm.acquirer.netc.enums.ExceptionCode;
import com.paytm.transport.Constants;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import com.paytm.transport.metrics.Monitor;
import io.lettuce.core.RedisException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.paytm.acquirer.netc.util.Constants.DIFF_KEY_PREFIX;
import static com.paytm.acquirer.netc.util.Constants.USE_SFTP_FOR_DIFF;
import static com.paytm.acquirer.netc.util.Constants.INIT_KEY_PREFIX;
import static com.paytm.acquirer.netc.util.Constants.SFTP_INIT_KEY_PREFIX;
import static com.paytm.acquirer.netc.util.Constants.PLAZA_TTL;
import static com.paytm.acquirer.netc.util.Utils.getKeyFromPrefixAndMsgId;

@Service
@RequiredArgsConstructor
@Retryable(
    maxAttemptsExpression = "#{${redis.retry.max-attempts}}",
    backoff = @Backoff(delayExpression = "#{${redis.retry.delay-ms}}"))
public class RedisService {
  private static final Logger log = LoggerFactory.getLogger(RedisService.class);
  private final RedisTemplate<String, Object> redisTemplate;
  private final RedisTemplate<String, RedisTag> queryExceptionDataTemplate;
  private final RedisTemplate<String, Integer> commonRedisTemplate;
  private static final String TIMED_OUT_MSG_KEY = "%s:TIMEOUT";
  private final RedisTtlConfig redisTtlConfig;
  private final RedisTemplate<String, Integer> redisTagExceptionCodeTemplate;
  private final RedisTemplate<String, IinInfo> redisIinTemplate;
  
  @Value("${init-diff-sync-key}")
  private String initDiffRedisKey;
  
  private static final int REDIS_RETRY = 2;
  
  @Monitor(name = "saveGetExceptionData", metricGroup = Monitor.ServiceGroup.REDIS)
  public void saveGetExceptionData(String key, List<?> values) {

    int count = 0;
    do {
      try {
        if (!values.isEmpty()) {
          redisTemplate.opsForSet().add(key, values.toArray());
          redisTemplate.expire(key, redisTtlConfig.getInitDataTtl(), TimeUnit.SECONDS);
        }
        return;
      } catch (Exception ex) {
        log.error("error when trying to call saveGetExceptionData redis", ex);
        count++;
      }
    } while (count < REDIS_RETRY);
    throw new RedisException("Error in executing saveGetExceptionData redis queries");
  }
  
  @Monitor(name = "saveGetExceptionDataMap", metricGroup = Monitor.ServiceGroup.REDIS)
  public void saveGetExceptionDataMap(String key, Map<String, Integer> mapOfTagIdAndExceptionCodeSum) throws RedisException {
    int count = 0;
    do {
      try {
        if (!CollectionUtils.isEmpty(mapOfTagIdAndExceptionCodeSum)) {
          redisTagExceptionCodeTemplate.opsForHash().putAll(key, mapOfTagIdAndExceptionCodeSum);
          redisTagExceptionCodeTemplate.expire(key, redisTtlConfig.getInitDataTtl(), TimeUnit.SECONDS);
        }
        return;
      } catch (Exception ex) {
        log.error("error when trying to call saveGetExceptionDataMap redis", ex);
        count++;
      }
    } while (count < REDIS_RETRY);
    throw new RedisException("Error in executing saveGetExceptionDataMap redis queries");
  }
  
  
  @Monitor(name = "getInitTagsExceptionCodesFromMap", metricGroup = Monitor.ServiceGroup.REDIS)
  public List<Integer> getInitTagsExceptionCodesFromMap(String key, List<String> tagIdList) throws RedisException {
    int count = 0;
    do {
      try {
        if (CollectionUtils.isEmpty(tagIdList)) {
          return Collections.emptyList();
        }
        HashOperations<String, String, Integer> hashOperations = redisTagExceptionCodeTemplate.opsForHash();
        return hashOperations.multiGet(key, tagIdList);
      } catch (Exception ex) {
        log.error("error when trying to call getInitTagsExceptionCodesFromMap redis", ex);
        count++;
      }
    } while (count < REDIS_RETRY);
    throw new RedisException("Error in executing getInitTagsExceptionCodesFromMap redis queries");
  }
  
  
  @Monitor(name = "saveQueryExceptionData", metricGroup = Monitor.ServiceGroup.REDIS)
  public void saveQueryExceptionData(String key, List<RedisTag> values) {
    if (!values.isEmpty()) {
      redisTemplate.opsForSet().add(key, values.toArray());
      redisTemplate.expire(key, redisTtlConfig.getDiffDataTtl(), TimeUnit.SECONDS);
    }
  }

  public Long getQueryExceptionTagsCount(String key) {
    return queryExceptionDataTemplate.opsForSet().size(key);
  }

  public List<RedisTag> getQueryExceptionTags(String key, Long count) {
    Set<RedisTag> tags = queryExceptionDataTemplate.opsForSet().members(key);
    if (CollectionUtils.isEmpty(tags)) {
      return Collections.emptyList();
    }
    if (!Objects.equals(tags.size(), count.intValue())) {
      log.warn("Excepted {} tags in key {} but found {}", count, key, tags.size());
    }
    return new ArrayList<>(tags);
  }

  @Monitor(name = "purgeInitData", metricGroup = Monitor.ServiceGroup.REDIS)
  public void purgeInitData(String msgId) {
    for (ExceptionCode code : ExceptionCode.values()) {
      String key = getKeyFromPrefixAndMsgId(code.getValue(), msgId);
      log.info("purging data for msgId:{}", key);
      redisTemplate.delete(key);
    }
  }

  public void purgeDiffData(String msgId) {
    String key = getKeyFromPrefixAndMsgId(DIFF_KEY_PREFIX, msgId);
    log.info("purging data for key:{}", key);
    redisTemplate.delete(key);
  }

  public boolean isMsgIdTimedOut(String messageId) {
    return redisTemplate.opsForValue().get(String.format(TIMED_OUT_MSG_KEY, messageId)) != null;
  }

  public void timeoutMsgId(String messageId) {
    log.info("MsgId {} got timedOut. Marking it in Redis", messageId);
    redisTemplate
        .opsForValue()
        .set(
            String.format(TIMED_OUT_MSG_KEY, messageId),
            true,
            redisTtlConfig.getMsgIdTtl(),
            TimeUnit.SECONDS);
  }

  public void removeInitInProgressFlag() {
    log.info("INIT in progress via API redis key: {} deleted", initDiffRedisKey);
    redisTemplate.delete(initDiffRedisKey);
  }

  public void setInitInProgressFlag(String messageId) {
    redisTemplate
        .opsForValue()
        .set(initDiffRedisKey, messageId, redisTtlConfig.getInitInProgressTtl(), TimeUnit.SECONDS);
  }

  public boolean isInitInProgress() {
    return redisTemplate.opsForValue().get(initDiffRedisKey) != null;
  }

  public String getInitInProgressAPIMsgId() {
    return (String) redisTemplate.opsForValue().get(initDiffRedisKey);
  }

  public void setInitCompletionFlag(boolean value) {
    String key = INIT_KEY_PREFIX + Timestamp.valueOf(LocalDate.now().atStartOfDay()).getTime();
    redisTemplate
        .opsForValue()
        .set(key, value, redisTtlConfig.getInitCompletionTtl(), TimeUnit.SECONDS);
    log.info("INIT Completed flag set");
  }

  public boolean isInitCompletionFlagExists() {
    String key = INIT_KEY_PREFIX + Timestamp.valueOf(LocalDate.now().atStartOfDay()).getTime();
    return redisTemplate.opsForValue().get(key) != null;
  }

  public void setInitProcessedFlagViaSftp(boolean value) {
    String key = SFTP_INIT_KEY_PREFIX + Timestamp.valueOf(LocalDate.now().atStartOfDay()).getTime();
    redisTemplate
        .opsForValue()
        .set(key, value, redisTtlConfig.getSftpProcessedTtl(), TimeUnit.SECONDS);
  }

  public boolean isInitProcessedViaSftp() {
    String key = SFTP_INIT_KEY_PREFIX + Timestamp.valueOf(LocalDate.now().atStartOfDay()).getTime();
    return redisTemplate.opsForValue().get(key) != null;
  }

  public void removeInitSFTPFlag() {
    String key = SFTP_INIT_KEY_PREFIX + Timestamp.valueOf(LocalDate.now().atStartOfDay()).getTime();
    log.info("INIT in progress via SFTP redis key: {} deleted", key);
    redisTemplate.delete(key);
  }

  public void removeInitCompletionFlag() {
    String key = INIT_KEY_PREFIX + Timestamp.valueOf(LocalDate.now().atStartOfDay()).getTime();
    redisTemplate.delete(key);
    log.info("INIT Completion flag removed");
  }

  public long getTxnCounterForPlaza(String plazaId) {
    String redisKey = plazaId + "_TxnCounter";
    long counter = redisTemplate.opsForValue().increment(redisKey);
    redisTemplate.expire(redisKey, PLAZA_TTL, TimeUnit.HOURS);
    return counter;
  }

  public void resetAllPlazaCounters() {
    redisTemplate.delete(redisTemplate.keys("*_TxnCounter"));
  }

  public void removeKey(String key) {
    redisTemplate.delete(key);
    log.info("removed key {}", key);
  }

  public void setCircuitOpenKey() {
    commonRedisTemplate
        .opsForValue()
        .set(
            Constants.RedisConstants.NETC_CIRCUIT_BREAKER_KEY,
            1,
            redisTtlConfig.getCircuitBreakerCounterTtl(),
            TimeUnit.SECONDS);
  }

  public void removeCircuitOpenKey() {
    commonRedisTemplate.delete(Constants.RedisConstants.NETC_CIRCUIT_BREAKER_KEY);
    log.info("removed key {}", Constants.RedisConstants.NETC_CIRCUIT_BREAKER_KEY);
  }

  public void setFetchDiffViaAPI() {
    redisTemplate.opsForValue().set(USE_SFTP_FOR_DIFF, false);
  }
  
  @Monitor(name = "saveIinParticipantList", metricGroup = Monitor.ServiceGroup.REDIS)
  public void saveIinParticipantList(String key, List<IinInfo> iinInfoNewList) throws RedisException {
    int count = 0;
    do {
      try {
        if (!CollectionUtils.isEmpty(iinInfoNewList)) {
          redisTemplate.delete(key);
          redisTemplate.opsForSet().add(key, iinInfoNewList.toArray());
          redisTemplate.expire(key, redisTtlConfig.getIinParticipantListTtl(), TimeUnit.SECONDS);
          log.info("Saved Key {} in redis", key);
        }
        return;
      } catch (Exception ex) {
        log.error("error when trying to call saveIinParticipantList redis", ex);
        count++;
      }
    } while (count < REDIS_RETRY);
    throw new RedisException("Error in executing saveIinParticipantList redis queries");
  }
  
  @Monitor(name = "getAllListParticipant", metricGroup = Monitor.ServiceGroup.REDIS)
  public List<IinInfo> getListParticipantBank(String key) {
    try {
      Set<IinInfo> tags = redisIinTemplate.opsForSet().members(key);
      if (CollectionUtils.isEmpty(tags)) {
        return Collections.emptyList();
      }
      return new ArrayList<>(tags);
    } catch (Exception e) {
      log.error("Error in fetching data from List participant bank");
    }
    return Collections.emptyList();
  }
}
