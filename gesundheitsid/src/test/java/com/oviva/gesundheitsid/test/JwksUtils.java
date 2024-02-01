package com.oviva.gesundheitsid.test;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.oviva.gesundheitsid.crypto.ECKeyPair;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.List;

public class JwksUtils {

  private JwksUtils() {}

  public static JWKSet load(Path path) {

    try (var fin = Files.newInputStream(path)) {
      return JWKSet.load(fin);
    } catch (IOException | ParseException e) {
      throw new RuntimeException("failed to load JWKS from '%s'".formatted(path), e);
    }
  }

  public static JWKSet toJwks(ECKeyPair pair) {

    try {
      var jwk =
          new ECKey.Builder(Curve.P_256, pair.pub())
              .privateKey(pair.priv())
              .keyIDFromThumbprint()
              .build();

      return new JWKSet(List.of(jwk));
    } catch (JOSEException e) {
      throw new IllegalArgumentException("bad key", e);
    }
  }

  public static JWKSet toPublicJwks(ECKeyPair pair) {
    try {
      var jwk = new ECKey.Builder(Curve.P_256, pair.pub()).keyIDFromThumbprint().build();

      return new JWKSet(List.of(jwk));
    } catch (JOSEException e) {
      throw new IllegalArgumentException("bad key", e);
    }
  }
}
