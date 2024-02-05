package com.oviva.gesundheitsid.test;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import java.security.SecureRandom;
import java.util.Random;

public class ECKeyGenerator {

  private static final ECKey EXAMPLE = generateExample();

  private ECKeyGenerator() {}

  public static ECKey generate() {
    return generateP256(null);
  }

  public static ECKey example() {
    return EXAMPLE;
  }

  private static ECKey generateExample() {
    var notVerySecureRandom = new NotSoSecureRandom();
    return generateP256(notVerySecureRandom);
  }

  private static ECKey generateP256(SecureRandom sr) {
    try {
      return new com.nimbusds.jose.jwk.gen.ECKeyGenerator(Curve.P_256)
          .secureRandom(sr)
          .keyIDFromThumbprint(true)
          .generate();

    } catch (JOSEException e) {
      throw new RuntimeException("failed to generate key", e);
    }
  }

  private static class NotSoSecureRandom extends SecureRandom {

    private final Random prng = new Random(1337L);

    @Override
    public void nextBytes(byte[] bytes) {
      prng.nextBytes(bytes);
    }
  }
}
