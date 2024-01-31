package com.oviva.gesundheitsid.test;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.oviva.gesundheitsid.test.ECKeyPairGenerator.ECKeyPair;
import java.util.List;

public class JwksUtils {

  private JwksUtils() {}

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
