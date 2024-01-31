package com.oviva.gesundheitsid.crypto;

import static com.oviva.gesundheitsid.test.JwksUtils.toJwks;
import static com.oviva.gesundheitsid.test.JwsUtils.*;
import static com.oviva.gesundheitsid.test.JwsUtils.garbageSignature;
import static com.oviva.gesundheitsid.test.JwsUtils.tamperSignature;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.jwk.JWKSet;
import com.oviva.gesundheitsid.test.ECKeyPairGenerator;
import java.text.ParseException;
import org.junit.jupiter.api.Test;

class JwsVerifierTest {

  private static ECKeyPair ECKEY = ECKeyPairGenerator.generate();

  @Test
  void verifyEmptyJwks() {

    var jwks = new JWKSet();
    assertFalse(JwsVerifier.verify(jwks, null));
  }

  @Test
  void verifyNoJwks() {

    assertThrows(IllegalArgumentException.class, () -> JwsVerifier.verify(null, null));
  }

  @Test
  void verify() throws ParseException {

    var jwks = toJwks(ECKEY);

    var jws = toJws(jwks, "hello world?").serialize();

    var in = JWSObject.parse(jws);

    assertTrue(JwsVerifier.verify(jwks, in));
  }

  @Test
  void verifyBadSignature() throws ParseException {

    var jwks = toJwks(ECKEY);

    var jws = toJws(jwks, "test").serialize();

    jws = tamperSignature(jws);

    var in = JWSObject.parse(jws);

    // when & then
    assertFalse(JwsVerifier.verify(jwks, in));
  }

  @Test
  void verifyUnknownKey() throws ParseException {

    var trustedJwks = toJwks(ECKEY);

    var signerJwks = toJwks(ECKeyPairGenerator.generate());

    var jws = toJws(signerJwks, "test").serialize();

    jws = tamperSignature(jws);

    var in = JWSObject.parse(jws);

    // when & then
    assertFalse(JwsVerifier.verify(trustedJwks, in));
  }

  @Test
  void verifyGarbageSignature() throws ParseException {
    var jwks = toJwks(ECKEY);

    var jws = toJws(jwks, "test").serialize();
    jws = garbageSignature(jws);

    var in = JWSObject.parse(jws);

    // when & then
    assertFalse(JwsVerifier.verify(jwks, in));
  }

  @Test
  void verify_badAlg() {

    var jwks = toJwks(ECKEY);

    var h = new JWSHeader(JWSAlgorithm.RS256);
    var in = new JWSObject(h, new Payload("hello?"));

    // when
    var e = assertThrows(UnsupportedOperationException.class, () -> JwsVerifier.verify(jwks, in));

    // then
    assertEquals("only supports ES256, found: RS256", e.getMessage());
  }
}
