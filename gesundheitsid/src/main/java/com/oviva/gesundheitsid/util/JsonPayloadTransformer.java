package com.oviva.gesundheitsid.util;

import com.nimbusds.jose.Payload;
import com.nimbusds.jose.PayloadTransformer;

public class JsonPayloadTransformer<T> implements PayloadTransformer<T> {

  private final Class<T> clazz;
  private final JsonReader reader;

  public JsonPayloadTransformer(Class<T> clazz, JsonReader reader) {
    this.clazz = clazz;
    this.reader = reader;
  }

  @Override
  public T transform(Payload payload) {
    return reader.readValue(payload.toBytes(), clazz);
  }

  public interface JsonReader {

    <T> T readValue(byte[] in, Class<T> clazz);
  }
}
