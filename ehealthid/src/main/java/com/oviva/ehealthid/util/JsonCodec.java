package com.oviva.ehealthid.util;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonCodec {

  private static final Logger logger = LoggerFactory.getLogger(JsonCodec.class);
  private static ObjectMapper om;
  private static ObjectMapper debugOm;

  static {
    var om = new ObjectMapper();

    om.registerModule(new JoseModule());
    om.setSerializationInclusion(Include.NON_NULL);
    om.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    if (logger.isDebugEnabled()) {
      logger.debug("debug level enabled, including source location in error messages");
      debugOm = om.copy();
      debugOm.enable(JsonParser.Feature.INCLUDE_SOURCE_IN_LOCATION);
    }

    JsonCodec.om = om;
  }

  private JsonCodec() {}

  public static String writeValueAsString(Object value) {
    try {
      return om.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new SerializeException("failed to serialize value to JSON", e);
    }
  }

  public static <T> T readValue(byte[] in, Class<T> clazz) {
    try {
      if (debugOm != null) {
        return debugOm.readValue(in, clazz);
      }
      return om.readValue(in, clazz);
    } catch (IOException e) {
      throw new DeserializeException("failed to deserialize JSON", e);
    }
  }

  public static class JsonException extends RuntimeException {

    public JsonException(String message) {
      super(message);
    }

    public JsonException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static class DeserializeException extends JsonException {

    public DeserializeException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static class SerializeException extends JsonException {

    public SerializeException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
