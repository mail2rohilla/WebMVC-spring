package com.paytm.acquirer.netc.service.common;

import com.paytm.acquirer.netc.Application;
import com.paytm.acquirer.netc.dto.common.HeaderXml;
import com.paytm.acquirer.netc.dto.common.TransactionXml;
import com.paytm.acquirer.netc.dto.manageException.ReqMngExceptionXml;
import com.paytm.acquirer.netc.dto.retry.RetryDto;
import com.paytm.acquirer.netc.enums.NetcEndpoint;
import com.paytm.acquirer.netc.util.Constants;
import com.paytm.acquirer.netc.util.JsonUtil;
import com.paytm.acquirer.netc.util.Utils;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.UUID;

import static com.paytm.acquirer.netc.enums.NetcEndpoint.RESP_QUERY_EXCEPTION_LIST;
import static com.paytm.acquirer.netc.util.Constants.NETC_API_VERSION;

@Service
@RequiredArgsConstructor
public class MetadataService {
  private static final Logger log = LoggerFactory.getLogger(Application.class);

  private static final SecureRandom random = new SecureRandom();
  private final RedisService redisService;

  @Value("${netc.paytm.org-id}")
  private String orgId;

  public String getTxnId(NetcEndpoint endpoint) {

    String api = getEndpointHash(endpoint);

    InetAddress inetAddress;
    String ipWithoutDots = "127001";
    try {
      inetAddress = InetAddress.getLocalHost();
      ipWithoutDots = inetAddress.getHostAddress().replace(".", "");
    } catch (UnknownHostException e) {
      log.error("exception:", e);
    }

    String ipHash = DigestUtils.sha256Hex(ipWithoutDots).substring(63);//this can be improved.
    String timeStamp = Long.toString(System.currentTimeMillis());
    String rand = String.format("%05d", random.nextInt(99999));
    String threadId = String.format("%02d", Thread.currentThread().getId() % 100);

    return threadId + timeStamp + rand + ipHash + api;
  }

  private String getEndpointHash(NetcEndpoint endpoint) {
    String hash;
    switch (endpoint) {
      case REQ_PAY:
        hash = "P";
        break;
      case REQ_DETAILS:
        hash = "D";
        break;
      case REQ_TXN_STATUS:
        hash = "T";
        break;
      case GET_EXCEPTION_LIST:
        hash = "G";
        break;
      case QUERY_EXCEPTION_LIST:
        hash = "Q";
        break;
      case MNG_EXCEPTION:
        hash = "M";
        break;
      case LIST_PARTICIPANT:
        hash = "L";
        break;
      default:
        throw new IllegalArgumentException("NETC Endpoint not passed in txnCalculation");
    }
    return hash;
  }

  private String getMsgId(String txnId, Integer retryCount) {
    if (txnId == null) {
      //todo why do we have this?
      txnId = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
    return "MSG" + String.format("%02d", retryCount) + txnId + String.valueOf(System.currentTimeMillis()).substring(10);
  }

  public void updateCounter() {
    log.info("updating plaza counter to zero for all plaza.");
    redisService.resetAllPlazaCounters();
  }

  public String getOrgTxnId(String txnId) {
    return "ORGTXN" + txnId;
  }

  public String getTxnCounter(String plazaId) {
    long counter = 6379; // Default counter (redis port number) to use in case of Redis issue
    try {
      counter = redisService.getTxnCounterForPlaza(plazaId);
    } catch (Exception ex) {
      log.error("Unable to fetch txn counter from redis, using default one", ex);
    }
    return String.format("%04d", counter % 10000);
  }

  public HeaderXml createXmlHeader(String txnId, String timeStamp, String msgId) {
    return createXmlHeader(txnId, timeStamp, msgId, 0);
  }

  /**
   * Create common header for XML request object
   *
   * @param txnId     Transaction ID create earlier during XML conversion (Don't request it again)
   * @param timeStamp TimeStamp (in NETC format), common created during request processing
   * @param msgId     TODO: REMOVE IT, USED ONLY FOR MOCK TESTING
   * @return Instantiated {@code HeaderXml} object
   */
  public HeaderXml createXmlHeader(String txnId, String timeStamp, String msgId, Integer retryCount) {
    HeaderXml header = new HeaderXml();
    // TODO: REMOVE THIS CODE AFTER MOCK TESTING
    if (StringUtils.hasLength(msgId)) {
      header.setMessageId(msgId);
    } else {
      header.setMessageId(getMsgId(txnId, retryCount));
    }
    header.setOrganizationId(orgId);
    header.setTimeStamp(timeStamp);
    header.setVersion(NETC_API_VERSION);

    return header;
  }

  public void updateTransaction(TransactionXml transaction, NetcEndpoint netcEndpoint) {
    transaction.setId(getTxnId(netcEndpoint));
    Utils.insertBlankForNullStings(transaction);
  }

  public void updateTransaction(ReqMngExceptionXml.MngExceptionTransactionXml transaction, NetcEndpoint netcEndpoint) {
    transaction.setId(getTxnId(netcEndpoint));
    Utils.insertBlankForNullStings(transaction);
  }

  public void updateTransactionReqPay(String txnId, TransactionXml transaction) {
    transaction.setId(txnId);
    Utils.insertBlankForNullStings(transaction);
  }

  /**
   * This method checks if message Id is in legacy format
   * and extract Acquirer Txn Id based on that info.
   *
   * Note: Identification of legacy MsgId happens on the basis on a property of Acquirer Txn Id:
   *       last character of Acquirer Txn Id is always alphabetic
   * @param msgId Message Id
   * @return Acquirer Transaction Id
   */
  public static String getTxnIdFromMsgId(String msgId) {
    if (Character.isDigit(msgId.charAt(3+21)))
      return msgId.substring(5, 5 + 22);
    else
      return msgId.substring(3, 3 + 22);
  }

  public static int getRetryCountFromMsgId(String msgId) {
    String count = msgId.substring(3, 5);
    return Integer.parseInt(count);
  }
  
  public static String getMetaData(NetcEndpoint endpoint, String uid, RetryDto retryDto) {
    String meta = null;
    if(endpoint == RESP_QUERY_EXCEPTION_LIST){
      try {
        meta = JsonUtil.serialiseJson(Collections.singletonMap(Constants.UID, uid));
      } catch (Exception e) {
        log.error(
          "Error while serializing additional param for messageId {} : {} ",retryDto.getMsgId(),
          e.getMessage());
      }
    }
    return meta;
  }
}
