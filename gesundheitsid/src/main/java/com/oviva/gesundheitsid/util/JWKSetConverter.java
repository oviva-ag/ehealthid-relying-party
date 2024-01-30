package com.oviva.gesundheitsid.util;

import com.fasterxml.jackson.databind.util.StdConverter;
import com.nimbusds.jose.jwk.JWKSet;
import java.util.Map;

public class JWKSetConverter extends StdConverter<JWKSet, Map<String, Object>> {

  @Override
  public Map<String, Object> convert(JWKSet value) {
    if (value == null) {
      return null;
    }
    return value.toJSONObject(false);
  }
}
