package com.oviva.gesundheitsid.util;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

public class JsonCodec {

  private static ObjectMapper om;

  static {
    var om = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    om.registerModule(new JoseModule());
    om.setSerializationInclusion(Include.NON_NULL);

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

  public static <T> T readValue(String in, Class<T> clazz) {
    try {
      return om.readValue(in, clazz);
    } catch (IOException e) {
      throw new DeserializeException("failed to deserialize JSON", e);
    }
  }

  public static <T> T readValue(byte[] in, Class<T> clazz) {
    try {
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
