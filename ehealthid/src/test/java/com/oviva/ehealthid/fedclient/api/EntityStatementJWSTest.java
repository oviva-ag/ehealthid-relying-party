package com.oviva.ehealthid.fedclient.api;

import static com.oviva.ehealthid.test.B64Utils.toB64;
import static com.oviva.ehealthid.util.JwsUtils.tamperSignature;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.jwk.JWKSet;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class EntityStatementJWSTest {

  private String EXAMPLE_ES =
      "eyJ0eXAiOiJlbnRpdHktc3RhdGVtZW50K2p3dCIsImtpZCI6InB1a19mZWRtYXN0ZXJfc2lnIiwiYWxnIjoiRVMyNTYifQ.eyJpc3MiOiJodHRwczovL2FwcC1yZWYuZmVkZXJhdGlvbm1hc3Rlci5kZSIsInN1YiI6Imh0dHBzOi8vYXBwLXJlZi5mZWRlcmF0aW9ubWFzdGVyLmRlIiwiaWF0IjoxNzA0ODczNjgxLCJleHAiOjE3MDQ5NjAwODEsImp3a3MiOnsia2V5cyI6W3sia3R5IjoiRUMiLCJjcnYiOiJQLTI1NiIsIngiOiJjZElSOGRMYnFhR3J6Zmd5dTM2NUtNNXMwMHpqRnE4REZhVUZxQnZyV0xzIiwieSI6IlhWcDF5U0oya2pFSW5walRaeTB3RDU5YWZFWEVMcGNrMGZrN3ZyTVdyYnciLCJraWQiOiJwdWtfZmVkbWFzdGVyX3NpZyIsInVzZSI6InNpZyIsImFsZyI6IkVTMjU2In1dfSwibWV0YWRhdGEiOnsiZmVkZXJhdGlvbl9lbnRpdHkiOnsiZmVkZXJhdGlvbl9mZXRjaF9lbmRwb2ludCI6Imh0dHBzOi8vYXBwLXJlZi5mZWRlcmF0aW9ubWFzdGVyLmRlL2ZlZGVyYXRpb24vZmV0Y2giLCJmZWRlcmF0aW9uX2xpc3RfZW5kcG9pbnQiOiJodHRwczovL2FwcC1yZWYuZmVkZXJhdGlvbm1hc3Rlci5kZS9mZWRlcmF0aW9uL2xpc3QiLCJpZHBfbGlzdF9lbmRwb2ludCI6Imh0dHBzOi8vYXBwLXJlZi5mZWRlcmF0aW9ubWFzdGVyLmRlL2ZlZGVyYXRpb24vbGlzdGlkcHMifX19.FY4GTGNfvG5rUVgsEw-UyBKWwfqa_wHxveqR2eQvdPk70Ix9ckJ4iMbC50QxljFXDYWiYCP01r8_wlBltBByAQ";

  static List<TC> testcases() {
    return List.of(
        new TC("empty", "", RuntimeException.class),
        new TC("blank", " \t\n ", RuntimeException.class),
        new TC("too short", "ab.ab.ab", RuntimeException.class),
        new TC("bad characters", ":D@#$%^&*(", RuntimeException.class));
  }

  @Test
  void parses_simple() {
    var jws = EntityStatementJWS.parse(EXAMPLE_ES);

    var iss = jws.body().iss();
    assertEquals("https://app-ref.federationmaster.de", iss);
  }

  @ParameterizedTest
  @MethodSource("testcases")
  void parse_bad(TC t) {
    assertThrows(t.expected(), () -> EntityStatementJWS.parse(t.in()), t.description());
  }

  @Test
  void badType() {

    var raw = toB64("{\"typ\":\"?\"}") + "." + toB64("{}") + ".abc";

    assertThrows(Exception.class, () -> EntityStatementJWS.parse(raw));
  }

  @Test
  void validAt() {

    var now = Instant.ofEpochSecond(1705942785);

    var nbf = now.minusSeconds(17);
    var exp = now.plusSeconds(391);

    var jws =
        new EntityStatementJWS(
            null,
            new EntityStatement(
                null, null, 0, exp.getEpochSecond(), nbf.getEpochSecond(), null, null, null));

    var afterExpiration = exp.plusSeconds(1);
    var beforeValidity = nbf.minusSeconds(1);

    // when & then
    assertTrue(jws.isValidAt(now));
    assertFalse(jws.isValidAt(afterExpiration));
    assertFalse(jws.isValidAt(beforeValidity));
  }

  @Test
  void verifySelfSigned() {

    var jws = EntityStatementJWS.parse(EXAMPLE_ES);
    assertTrue(jws.verifySelfSigned());
  }

  @Test
  void verifyBadSignature() {
    var es = tamperSignature(EXAMPLE_ES);

    var jws = EntityStatementJWS.parse(es);
    assertFalse(jws.verifySelfSigned());
  }

  @Test
  void verifyBadKey() {
    var es = tamperSignature(EXAMPLE_ES);

    var jws = EntityStatementJWS.parse(es);
    assertFalse(jws.verifySignature(new JWKSet()));
  }

  record TC(String description, String in, Class<? extends Throwable> expected) {}
}
