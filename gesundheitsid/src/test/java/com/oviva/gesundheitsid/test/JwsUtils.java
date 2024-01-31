package com.oviva.gesundheitsid.test;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.JWKSet;

public class JwsUtils {

  private JwsUtils() {}

  public static JWSObject toJws(JWKSet jwks, String payload) {
    try {
      var key = jwks.getKeys().get(0);
      var signer = new ECDSASigner(key.toECKey());

      var h = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.getKeyID()).build();

      var jwsObject = new JWSObject(h, new Payload(payload));
      jwsObject.sign(signer);

      return jwsObject;
    } catch (JOSEException e) {
      throw new IllegalArgumentException("failed to sign payload", e);
    }
  }

  public static String tamperSignature(String jws) {
    var raw = jws.toCharArray();
    raw[raw.length - 3] = flipSecondBit(raw[raw.length - 3]);
    return new String(raw);
  }

  public static String garbageSignature(String jws) {
    var splits = jws.split("\\.");
    return splits[0] + "." + splits[1] + ".garbage";
  }

  private static char flipSecondBit(char a) {
    return (char) ((int) a ^ 0x1);
  }
}
