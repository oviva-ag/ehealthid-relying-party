package com.oviva.gesundheitsid.relyingparty.util;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Base64.Encoder;

public class IdGenerator {

  private static final SecureRandom sr = new SecureRandom();
  private static final Encoder encoder = Base64.getUrlEncoder().withoutPadding();

  private IdGenerator() {}

  public static String generateID() {
    var raw = new byte[32];
    sr.nextBytes(raw);
    return encoder.encodeToString(raw);
  }
}
