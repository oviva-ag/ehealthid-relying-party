package com.oviva.gesundheitsid.crypto;

import static com.oviva.gesundheitsid.test.JwsUtils.garbageSignature;
import static com.oviva.gesundheitsid.test.JwsUtils.tamperSignature;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.oviva.gesundheitsid.test.ECKeyPairGenerator;
import com.oviva.gesundheitsid.test.ECKeyPairGenerator.ECKeyPair;
import java.io.IOException;
import java.text.ParseException;
import java.util.List;
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
  void verify() throws IOException, JOSEException, ParseException {

    var jwks = toJwks(ECKEY);

    var jws = toJws(jwks, "hello world?");

    var in = JWSObject.parse(jws);

    assertTrue(JwsVerifier.verify(jwks, in));
  }

  @Test
  void verifyBadSignature() throws JOSEException, ParseException {

    var jwks = toJwks(ECKEY);

    var jws = toJws(jwks, "test");

    jws = tamperSignature(jws);

    var in = JWSObject.parse(jws);

    // when & then
    assertFalse(JwsVerifier.verify(jwks, in));
  }

  @Test
  void verifyUnknownKey() throws JOSEException, ParseException {

    var trustedJwks = toJwks(ECKEY);

    var signerJwks = toJwks(ECKeyPairGenerator.generate());

    var jws = toJws(signerJwks, "test");

    jws = tamperSignature(jws);

    var in = JWSObject.parse(jws);

    // when & then
    assertFalse(JwsVerifier.verify(trustedJwks, in));
  }

  @Test
  void verifyGarbageSignature() throws JOSEException, ParseException {
    var jwks = toJwks(ECKEY);

    var jws = toJws(jwks, "test");
    jws = garbageSignature(jws);

    var in = JWSObject.parse(jws);

    // when & then
    assertFalse(JwsVerifier.verify(jwks, in));
  }

  private String toJws(JWKSet jwks, String payload) throws JOSEException {
    var key = jwks.getKeys().get(0);
    var signer = new ECDSASigner(key.toECKey());

    var h = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.getKeyID()).build();

    var jwsObject = new JWSObject(h, new Payload(payload));
    jwsObject.sign(signer);

    return jwsObject.serialize();
  }

  private JWKSet toJwks(ECKeyPair pair) throws JOSEException {

    var jwk =
        new ECKey.Builder(Curve.P_256, pair.pub())
            .privateKey(pair.priv())
            .keyIDFromThumbprint()
            .build();

    // JWK with extra steps, otherwise Keycloak can't deal with the parsed key
    return new JWKSet(List.of(jwk));
  }

  @Test
  void verify_badAlg() throws JOSEException {

    var jwks = toJwks(ECKEY);

    var h = new JWSHeader(JWSAlgorithm.RS256);
    var in = new JWSObject(h, new Payload("hello?"));

    // when
    var e = assertThrows(UnsupportedOperationException.class, () -> JwsVerifier.verify(jwks, in));

    // then
    assertEquals("only supports ES256, found: RS256", e.getMessage());
  }
}
