package com.paytm.acquirer.netc.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.MapType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.paytm.transport.logger.Logger;
import com.paytm.transport.logger.LoggerFactory;
import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@UtilityClass
public class JsonUtil {
  private static final Logger log = LoggerFactory.getLogger(JsonUtil.class);

  private static final ObjectMapper OBJECT_MAPPER;
  private static final TypeFactory TYPE_FACTORY;

  static {
    OBJECT_MAPPER = new ObjectMapper();
    OBJECT_MAPPER.enable(JsonGenerator.Feature.QUOTE_NON_NUMERIC_NUMBERS);
    OBJECT_MAPPER.enable(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS);
    OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    OBJECT_MAPPER.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
    OBJECT_MAPPER.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
    TYPE_FACTORY = TypeFactory.defaultInstance();
  }

  public static ObjectMapper getObjectMapper() {
    return OBJECT_MAPPER;
  }


  public static <T> T parseJson(String data, Class<T> clazz) {
    try {
      return OBJECT_MAPPER.readValue(data, clazz);
    } catch (IOException e) {
      log.error("Error in parseJson", e);
      return null;
    }
  }


  public static <T> Map<String, T> parseJsonToMapWithStringKey(String data) {
    try {
      return OBJECT_MAPPER.readValue(data, new TypeReference<Map<String, T>>() {
      });
    } catch (IOException e) {
      log.error("Error in parseJsonToMapWithStringKey", e);
      return Collections.emptyMap();
    }
  }

  public static <K, V> Map<K, V> parseJsonToMap(String data, Class<K> keyClass, Class<V> valueClass) {
    JavaType kt = TYPE_FACTORY.constructType(keyClass);
    JavaType vt = TYPE_FACTORY.constructType(valueClass);
    try {
      return OBJECT_MAPPER.readValue(data, MapType.construct(HashMap.class, null, null, null, kt, vt));
    } catch (IOException e) {
      log.error("Error in parseJsonToMap", e);
      return Collections.emptyMap();
    }
  }

  public static <T> List<T> parseJsonToList(String data, Class<T> elementClass) {
    try {
      return OBJECT_MAPPER.readValue(data, CollectionType.construct(ArrayList.class, null, null, null, TYPE_FACTORY.constructType(elementClass)));
    } catch (IOException e) {
      log.error("Error in parseJsonToList", e);
      return Collections.emptyList();
    }
  }

  public static <T> Set<T> parseJsonToSet(String data, Class<T> elementClass) {
    try {
      return OBJECT_MAPPER.readValue(data, CollectionType.construct(HashSet.class, null, null, null, TYPE_FACTORY.constructType(elementClass)));
    } catch (IOException e) {
      log.error("Error in parseJsonToSet", e);
      return Collections.emptySet();
    }
  }

  public static <T> T convertMapToPojo(Map<String, ?> map, Class<T> clazz) {
    return OBJECT_MAPPER.convertValue(map, clazz);
  }


  public static String jsonify(String field, String value) {
    if (value == null) {
      return "{\"" + field + "\":" + "null}";
    }
    return "{\"" + field + "\":" + "\"" + value + "\"}";
  }


  public static String serialiseJson(Object data) {
    try {
      return OBJECT_MAPPER.writeValueAsString(data);
    } catch (JsonProcessingException e) {
      log.error("Error in serialiseJson", e);
      return null;
    }
  }


}
