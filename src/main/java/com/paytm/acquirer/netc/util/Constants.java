package com.paytm.acquirer.netc.util;

import lombok.experimental.UtilityClass;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@UtilityClass
public class Constants {


  public static final String REQ_PAY = "REQ_PAY";
  public static final String TIME_SYNC_ERROR_CODE = "205";
  public static final String TRANSACTION_COMBINATION_ERROR = "305";
  public static final String RESULT_SUCCESS = "SUCCESS";
  public static final String RESULT_FAILURE = "FAILURE";
  public static final String RESULT_PARTIAL = "PARTIAL";
  public static final String PARTICIPANT_NOT_IN_DB_ERROR = "316";
  public static final String ALL_PARTICIPANT_CODE = "ALL";
  public static final String V1 = "/v1";
  public static final String BASE_URL = "/api/netc-engine" + V1;

  @UtilityClass
  public static class VehicleDetails {
    public static final String BANK_ID = "BANKID";
    public static final String COMMERCIAL_VEHICLE = "COMVEHICLE";
    public static final String EXEMPTION_CODE = "EXCCODE";
    public static final String ISSUE_DATE = "ISSUEDATE";
    public static final String REG_NO = "REGNUMBER";
    public static final String TAG_ID = "TAGID";
    public static final String VEHICLE_CLASS = "VEHICLECLASS";
    public static final String TID = "TID";
    public static final String TAG_STATUS = "TAGSTATUS";
  }

  @UtilityClass
  public static class ReqPay {
    public static final String SIGN_AUTH = "VALID";
    public static final String TAG_VERIFIED = "NETC TAG";
    public static final String VEHICLE_AUTH = "YES";
    public static final String TXN_STATUS = "SUCCESS";
    public static final String MERCH_TYPE = "TOLL";
    public static final String PAYEE_TYPE = "MERCHANT";
    public static final String PAYER_TYPE = "PERSON";
    public static final String PAYEE_ADDR_SUFFIX = "@iin.npci";
    public static final String CURRENCY = "INR";

    public static final String PAYMENT_REQ_START = "PAYREQSTART";
    public static final String PAYMENT_REQ_END = "PAYREQEND";
    public static final String STATIC_WEIGHT = "STATIC_WEIGHT";

    public static final String PAYER_ADDR_FMT = "%s@%s.iin.npci";
    public static final String TXN_PENDING = "PENDING";
    public static final String CHECK_TXN_RETRY_COUNT = "CHECK_TXN_RETRY_COUNT";
  }

  @UtilityClass
  public static class RetryParams {
    public static final String ACQUIRER_ID = "acquirerId";
    public static final String ISSUER_ID = "issuerId";
    public static final String TXN_DATE = "txnDate";
    public static final String API = "api";
    public static final String MSG_NUM = "msgNum";
    public static final String TOTAL_MSG = "totalMsg";
    public static final String RETRY_COUNT = "retryCount";
    public static final String REF_ID = "refId";
    public static final String READER_READ_TIME = "readerReadTime";
    public static final String NETC_TXN_ID = "netcTxnId";
  }

  @UtilityClass
  public static class KafkaConstants {
    public static final String TYPE = "type";
    public static final String INIT = "INIT";
    public static final String DIFF = "DIFF";
  }

  @UtilityClass
  public static class DynamicConfigKey {
    //TIMEOUTS
    public static final String RESP_PAY_TIMEOUT = "RESP_PAY_TIMEOUT";
    public static final String NPCI_AUTO_RETRY_TIMEOUT = "NPCI_AUTO_RETRY_TIMEOUT";
    public static final String FIRST_RESP_GET_EXCEPTION_TIMEOUT = "FIRST_RESP_GET_EXCEPTION_TIMEOUT";
    public static final String CONSECUTIVE_RESP_GET_EXCEPTION_TIMEOUT = "CONSECUTIVE_RESP_GET_EXCEPTION_TIMEOUT";
    public static final String FIRST_RESP_QUERY_EXCEPTION_TIMEOUT = "FIRST_RESP_QUERY_EXCEPTION_TIMEOUT";
    public static final String CONSECUTIVE_RESP_QUERY_EXCEPTION_TIMEOUT = "CONSECUTIVE_RESP_QUERY_EXCEPTION_TIMEOUT";
    public static final String REQ_GET_EXCEPTION_LOCAL_RETRY_TIMEOUT = "REQ_GET_EXCEPTION_LOCAL_RETRY_TIMEOUT";
    public static final String REQ_QUERY_EXCEPTION_LOCAL_RETRY_TIMEOUT = "REQ_QUERY_EXCEPTION_LOCAL_RETRY_TIMEOUT";
    // MAX RETRIES
    public static final String REQ_PAY_MAX_RETRY = "REQ_PAY_MAX_RETRY";
    public static final String REQ_GET_EXCEPTION_MAX_RETRY = "REQ_GET_EXCEPTION_MAX_RETRY";
    public static final String REQ_QUERY_EXCEPTION_MAX_RETRY = "REQ_QUERY_EXCEPTION_MAX_RETRY";
    public static final String TIME_SYNC_MAX_RETRY = "TIME_SYNC_MAX_RETRY";
    public static final String CHECK_TXN_STATUS_MAX_RETRY = "CHECK_TXN_STATUS_MAX_RETRY";
    public static final String CHECK_TXN_STATUS_REQ_PAY_MAX_RETRY = "CHECK_TXN_STATUS_REQ_PAY_MAX_RETRY";
    public static final String REQ_PAY_LOCAL_MAX_RETRY = "REQ_PAY_LOCAL_MAX_RETRY";
    public static final String INIT_LOG_SIZE = "INIT_LOG_SIZE";
    public static final String NPCI_SFTP_FILES_PATH = "NPCI_SFTP_FILES_PATH";

  }

  public static final String SUCCESS_RESPONSE_CODE = "000";
  public static final String TRANSACTION_ACCEPTED = "ACCEPTED";
  public static final String TRANSACTION_DEEMED_ACCEPTED = "DEEMED ACCEPTED";
  public static final String TRANSACTION_DECLINED = "DECLINED";
  public static final List<String> SUCCESS_RESPONSE_CODE_LIST = Arrays.asList("000", "00");
  public static final List<String> SUCCESS_STATUS_LIST = Arrays.asList(TRANSACTION_ACCEPTED, TRANSACTION_DEEMED_ACCEPTED);
  public static final List<String> VALID_TXN_STATUS_LIST = Collections.unmodifiableList(Arrays.asList(
    TRANSACTION_DEEMED_ACCEPTED, TRANSACTION_ACCEPTED, TRANSACTION_DECLINED));

  public static final long SEC_IN_DAY = 86400l;

  public static final String SLASH_DELIMETER = "/";
  public static final String CSV_EXT = ".csv";
  public static final String SEQ_PREFIX = "00";
  public static final String SEQ_PREFIX_GT_9 = "0";
  public static final String HYPEN_STR = "-";
  public static final String ENC_FILENAME_PREFIX = "INIT_ENC-";
  public static final String DEC_FILENAME_PREFIX = "INIT_DEC-";
  public static final String MSG_PREFIX = "MSG";
  public static final String INIT_EXCEPTION_SET = "INIT_EXCEPTION_SET_";
  public static final String INIT_EXCEPTION_MAP = "INIT_EXCEPTION_MAP_";
  public static final String SFTP_INIT_SET_BATCH_FIXED_SIZE_KEY = "SFTP_INIT_SET_BATCH_FIXED_SIZE";
  public static final String IIN_PARTICIPANTS_KEY = "IIN_PARTICIPANTS_LIST";


  public static final String NETC_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
  public static final String NETC_ISSUE_DATE_INPUT_FORMAT = "yyyy-MM-dd";
  public static final String NETC_ISSUE_DATE_OUTPUT_FORMAT = "dd-MM-yyyy";
  public static final String NETC_DIFF_DATE_TIME_FORMAT = "ddMMyyyyHHmmss";
  public static final String NETC_DIFF_DATE_FORMAT = "ddMMyyyy";
  public static final String NETC_API_VERSION = "1.0";
  public static final String DIFF_KEY_PREFIX = "DIFF";
  public static final String INIT_KEY_PREFIX = "INIT_";
  public static final String SFTP_INIT_KEY_PREFIX = "SFTP_";
  public static final String USE_SFTP_FOR_DIFF = "use_sftp_for_diff";
  public static final String CHANNEL_DESC = "sftp";
  public static final Integer NETC_DIFF_FILE_LENGTH_AFTER_EXT = 3;
  public static final Integer NETC_DIFF_FILE_SEQUENCE_LENGTH = 3;

  public static final String FAILURE_METRIC = "FAILED";
  public static final String UID = "uid";
  public static final String LAST_UPDATED_TIME = "last_updated_time";
  public static final String TEST_REQUEST_VIA_API = "test_request_via_api";
  public static final String NPCI_RESPONSE_TIME = "NPCI_RESPONSE_TIME";
  public static final String PLAZA_ID = "plaza_id";
  public static final int PLAZA_TTL = 24;

  @UtilityClass
  public static final class DynamicConfig {

    @UtilityClass
    public static final class Key {
      public static final String MIN_DIFF_SUCCESS_VIA_TEST_API = "MIN_DIFF_SUCCESS_VIA_TEST_API";
      public static final String DIFF_FILE_BUFFER_TIME = "DIFF_FILE_BUFFER_TIME";
      public static final String INIT_SET_DIVISION_FACTOR = "INIT_SET_DIVISION_FACTOR";
    }

    @UtilityClass
    public static final class DefaultValue {
      public static final Integer MIN_DIFF_SUCCESS_VIA_TEST_API = 2;
      public static final Integer DIFF_FILE_BUFFER_TIME = 5;
      public static final Integer INIT_SET_DIVISION_FACTOR = 2;
      public static final Integer SFTP_INIT_SET_BATCH_FIXED_SIZE = 50000;
      public static final String NPCI_SFTP_FILES_PATH_VALUE = "/appdata1/ETC/ExceptionFiles/";
    }

  }

  public static final String REGEX_NEW_LINE = "[\\n]";

  @UtilityClass
  public static final class MetricConstants {

    public static final String ACQUIRER_ENTITY_TAG = "acquirer_entity";
    public static final String ENTITY_TYPE = "entity_type";
    public static final String STATUS = "status";
    public static final String MSG_ID = "msg_id";
    public static final String TOTAL_TAGS = "total_tags";
    public static final String TIME_TAKEN = "time_taken";
    public static final String START_TIME = "start_time";
    public static final String TOTAL_FILES = "total_files";
    public static final String TRANSITION = "transition";
    public static final String NETC_CIRCUIT_BREKER = "netc_circuit_breaker";
  
    public enum ACQUIRER_ENTITIES {
      UNKNOWN_MSG_ID,
      DIFF_RESP_TIME_TAKEN,
      DIFF_MSG_DETAIL
    }
  }

}
