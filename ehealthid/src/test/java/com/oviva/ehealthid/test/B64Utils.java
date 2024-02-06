package com.oviva.ehealthid.test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class B64Utils {

  private B64Utils() {}

  public static String toB64(String raw) {
    return Base64.getUrlEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }
}
