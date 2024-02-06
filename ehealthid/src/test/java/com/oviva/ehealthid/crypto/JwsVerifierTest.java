package com.oviva.ehealthid.crypto;

import static com.oviva.ehealthid.util.JwsUtils.*;
import static com.oviva.ehealthid.util.JwsUtils.garbageSignature;
import static com.oviva.ehealthid.util.JwsUtils.tamperSignature;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import java.text.ParseException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class JwsVerifierTest {

  private static ECKey ECKEY;
  private static JWKSet JWKS;

  @BeforeAll
  static void beforeAll() throws JOSEException {
    ECKEY = new ECKeyGenerator(Curve.P_256).keyIDFromThumbprint(true).generate();
    JWKS = new JWKSet(ECKEY);
  }

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

    var jws = toJws(ECKEY, "hello world?").serialize();

    var in = JWSObject.parse(jws);

    assertTrue(JwsVerifier.verify(JWKS, in));
  }

  @Test
  void verifyBadSignature() throws ParseException {

    var jws = toJws(ECKEY, "test").serialize();

    jws = tamperSignature(jws);

    var in = JWSObject.parse(jws);

    // when & then
    assertFalse(JwsVerifier.verify(JWKS, in));
  }

  @Test
  void verifyUnknownKey() throws ParseException, JOSEException {

    var signerJwks = new ECKeyGenerator(Curve.P_256).generate();

    var jws = toJws(signerJwks, "test").serialize();

    jws = tamperSignature(jws);

    var in = JWSObject.parse(jws);

    // when & then
    assertFalse(JwsVerifier.verify(JWKS, in));
  }

  @Test
  void verifyGarbageSignature() throws ParseException {

    var jws = toJws(ECKEY, "test").serialize();
    jws = garbageSignature(jws);

    var in = JWSObject.parse(jws);

    // when & then
    assertFalse(JwsVerifier.verify(JWKS, in));
  }

  @Test
  void verify_badAlg() {

    var h = new JWSHeader(JWSAlgorithm.RS256);
    var in = new JWSObject(h, new Payload("hello?"));

    // when
    var e = assertThrows(UnsupportedOperationException.class, () -> JwsVerifier.verify(JWKS, in));

    // then
    assertEquals("only supports ES256, found: RS256", e.getMessage());
  }
}
