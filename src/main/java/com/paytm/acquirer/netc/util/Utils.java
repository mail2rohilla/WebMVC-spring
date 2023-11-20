package com.paytm.acquirer.netc.util;

import com.paytm.acquirer.netc.dto.syncTime.TimeSyncResponse;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import com.paytm.transport.metrics.DataDogClient;
import lombok.experimental.UtilityClass;

import javax.validation.constraints.NotNull;
import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static com.paytm.acquirer.netc.util.Constants.*;

@UtilityClass
public class Utils {
  private static final Logger log = LoggerFactory.getLogger(Utils.class);
  public static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(NETC_DATE_FORMAT);
  private static final DateTimeFormatter issueDateInputFormatter = DateTimeFormatter.ofPattern(NETC_ISSUE_DATE_INPUT_FORMAT);
  private static final DateTimeFormatter issueDateOutputFormatter = DateTimeFormatter.ofPattern(NETC_ISSUE_DATE_OUTPUT_FORMAT);
  public static final DateTimeFormatter diffDateFormatter = DateTimeFormatter.ofPattern(NETC_DIFF_DATE_FORMAT);

  private static long netcEngineClockOffset = 0;

  public static <E extends Enum<E>, T> E getEnumFromItsProperty(T value, Class<E> enumName, Function<E, T> function) {
    if (Objects.isNull(value)) {
      throw new IllegalArgumentException("Supplied can not be null");
    }

    if (Objects.isNull(enumName)) {
      throw new IllegalArgumentException("Enum can not be null");
    }

    for (E e : enumName.getEnumConstants()) {
      if (function.apply(e).equals(value)) {
        return e;
      }
    }
    return null;
  }

  /**
   * get retry count from legacy Txn Id
   * @param txnId Acquirer Transaction Id
   * @return retry count
   */
  public static int getCounterFromTxnId(String txnId) {
    return Integer.parseInt(txnId.substring(0, 2));
  }

  /**
   * @return current time representation in NETC standard format
   */
  public static String getFormattedDate() {
    return getFormattedDate(LocalDateTime.now());
  }

  /**
   * @return current time representation in NETC standard format
   */
  public static String getFormattedDate(Long tasNpciTimeDiff) {
    return getFormattedDate(LocalDateTime.now().minusSeconds(tasNpciTimeDiff));
  }
  /**
   * @param dateTime {@link LocalDateTime} instance to format
   * @return Formatted date in NETC standard format
   */
  public static String getFormattedDate(@NotNull LocalDateTime dateTime) {
    return dateTime.plusSeconds(netcEngineClockOffset).format(dateTimeFormatter);
  }

  /**
   * @param dateTime {@link LocalDateTime} instance to format
   * @return Formatted date in NETC standard format Without offset
   */
  public static String getFormattedDateWithoutOffset(@NotNull LocalDateTime dateTime) {
    return dateTime.format(dateTimeFormatter);
  }

  /**
   * @param dateTime {@link LocalDateTime} instance to format
   * @return Formatted date in DIFF file NETC standard format
   */
  public static String getFormattedDiffDate(@NotNull LocalDateTime dateTime) {
    return dateTime.format(diffDateFormatter);
  }

  /**
   * Adjust the time difference in seconds as compared to NETC Switch Clock
   *
   * @param response Formatted time string from NETC Switch
   * @param dataDogClient To send metric data to dd.
   */
  public static void adjustClockOffset(TimeSyncResponse response, DataDogClient dataDogClient) {
    LocalDateTime time = LocalDateTime.parse(response.getServerTime(), dateTimeFormatter);
    LocalDateTime tasLocalServerTime = LocalDateTime.parse(response.getTasLocalServerTime(), dateTimeFormatter);
    netcEngineClockOffset = ChronoUnit.SECONDS.between(tasLocalServerTime,time);
    log.info("Time difference between tas server time and npci server time in second {}", netcEngineClockOffset);
    dataDogClient.recordExecutionMetricWithTime("timeSync", netcEngineClockOffset, "timeSync");
  }

  public static void insertBlankForNullStings(Object obj) {
    getAllFields(obj.getClass()).forEach(field -> {
      field.setAccessible(true);
      try {
        if (field.getType().equals(String.class)) {
          String s = (String) field.get(obj);
          if (s == null) {
            field.set(obj, "");
          }
        }
      } catch (IllegalAccessException iae) {
        log.error("Error while setting null strings to empty.", iae);
      }
    });
  }

  public static String transformDate(String input) {
    LocalDate time = LocalDate.parse(input, issueDateInputFormatter);
    return time.format(issueDateOutputFormatter);
  }

  public static String transformDateToNetcDate(String input) {
    LocalDateTime time = LocalDateTime.parse(input, dateTimeFormatter);
    return time.format(issueDateInputFormatter);
  }

  public static String getTodayDate() {
    return LocalDate.now().format(issueDateInputFormatter);
  }

  public static String getKeyFromPrefixAndMsgId(String prefix, String msgId) {
    return prefix + "_" + msgId;
  }

  private static List<Field> getAllFields(Class<?> classType) {
    List<Field> fields = new ArrayList<>();
    for (Class<?> c = classType; c != null; c = c.getSuperclass()) {
      fields.addAll(Arrays.asList(c.getDeclaredFields()));
    }
    return fields;
  }


  public static LocalDateTime getLocalDateTime(String dateString) {
    return LocalDateTime.parse(dateString, dateTimeFormatter);
  }

  public static long getTimeDiff(String dateString) {
    return ChronoUnit.SECONDS.between(getLocalDateTime(dateString),
        LocalDateTime.now().plusSeconds(netcEngineClockOffset));
  }

  public static boolean listContainItem(String csv, String s) {
    return Arrays.asList(csv.split(",")).contains(s);
  }

  public static boolean isPastDate(Timestamp timestamp) {
    return timestamp.toLocalDateTime().toLocalDate().isBefore(LocalDate.now());
  }

  public static long getNetcEngineClockOffset(){
    return netcEngineClockOffset;
  }
}
