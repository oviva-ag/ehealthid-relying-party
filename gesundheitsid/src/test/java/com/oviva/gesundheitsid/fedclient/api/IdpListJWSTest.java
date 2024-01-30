package com.oviva.gesundheitsid.fedclient.api;

import static com.oviva.gesundheitsid.test.B64Utils.toB64;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IdpListJWSTest {

  private static String EXAMPLE_IDP_LIST =
      "eyJ0eXAiOiJpZHAtbGlzdCtqd3QiLCJraWQiOiJwdWtfZmVkbWFzdGVyX3NpZyIsImFsZyI6IkVTMjU2In0.eyJpc3MiOiJodHRwczovL2FwcC10ZXN0LmZlZGVyYXRpb25tYXN0ZXIuZGUiLCJpYXQiOjE3MDU5NDI5MDQsImV4cCI6MTcwNjAyOTMwNCwiaWRwX2VudGl0eSI6W3siaXNzIjoiaHR0cHM6Ly9pZGJyb2tlci5pYm0udHUubm9ucHJvZC1laGVhbHRoLWlkLmRlIiwib3JnYW5pemF0aW9uX25hbWUiOiJJQk0iLCJsb2dvX3VyaSI6Imh0dHBzOi8vaWRicm9rZXIuaWJtLnR1Lm5vbnByb2QtZWhlYWx0aC1pZC5kZS9sb2dvLnBuZyIsInVzZXJfdHlwZV9zdXBwb3J0ZWQiOiJJUCIsInBrdiI6ZmFsc2V9LHsiaXNzIjoiaHR0cHM6Ly9nc2kuZGV2LmdlbWF0aWsuc29sdXRpb25zIiwib3JnYW5pemF0aW9uX25hbWUiOiJnZW1hdGlrIHNla3RvcmFsZXIgSURQIiwibG9nb191cmkiOiJodHRwczovL2dzaS5kZXYuZ2VtYXRpay5zb2x1dGlvbnMvbm9Mb2dvWWV0IiwidXNlcl90eXBlX3N1cHBvcnRlZCI6IklQIiwicGt2Ijp0cnVlfSx7ImlzcyI6Imh0dHBzOi8vb2lkYy52YXUudHUudGlydS5pZHAucmlzZS1zZXJ2aWNlLmRlLzQ1MDcwMTY4OSIsIm9yZ2FuaXphdGlvbl9uYW1lIjoiUklTRSIsImxvZ29fdXJpIjoiaHR0cHM6Ly9pZHAudHUudGlydS5pZHAucmlzZS1zZXJ2aWNlLmRlL2F1dGgvcmVzb3VyY2VzL2VhN2Q4ZmMvbG9naW4vZXBhLWtleWNsb2FrLXRoZW1lLzQ1MDcwMTY4OS9pbWcvc3RhcnRzY3JlZW5fbG9nby5wbmciLCJ1c2VyX3R5cGVfc3VwcG9ydGVkIjoiSVAiLCJwa3YiOmZhbHNlfSx7ImlzcyI6Imh0dHBzOi8vd3d3LmlkcC50dC5pYW0tYm1zLmRlLzEwNDAyNzU0NCIsIm9yZ2FuaXphdGlvbl9uYW1lIjoiQk1TIiwibG9nb191cmkiOiJodHRwczovL3R0LmlhbS1ibXMuZGUvYXV0aC9yZXNvdXJjZXMvNDM4ZjE5OC9sb2dpbi9lcGEta2V5Y2xvYWstdGhlbWUvMTA0MDI3NTQ0L2ltZy9zdGFydHNjcmVlbl9sb2dvLnBuZyIsInVzZXJfdHlwZV9zdXBwb3J0ZWQiOiJJUCIsInBrdiI6ZmFsc2V9LHsiaXNzIjoiaHR0cHM6Ly93ZWIudHUuaWQuZGlnaXRhbC5iYXJtZXIuZGUiLCJvcmdhbml6YXRpb25fbmFtZSI6IlZlcmltaSIsImxvZ29fdXJpIjoiaHR0cHM6Ly93ZWIudmVyaW1pLmRlL2ltYWdlcy92ZXJpbWktbG9nby1ncmVlbi5zdmciLCJ1c2VyX3R5cGVfc3VwcG9ydGVkIjoiSVAiLCJwa3YiOmZhbHNlfV19.KoVHxWclbSb0JX1N-ekz0w8FSkiiz1HM5JqrsBoXJ0hrb4ck9eyI6erSmBTB4HcNTeIPURAuveyB5UAngmZMMg";

  @Test
  void parse() {
    var now = Instant.parse("2024-01-22T17:07:13.705019Z");

    var jws = IdpListJWS.parse(EXAMPLE_IDP_LIST);

    assertTrue(jws.isValidAt(now));
    assertThat(jws.body().idpEntities(), hasSize(5));
  }

  @ParameterizedTest
  @ValueSource(strings = {"?", "a.a.a", "\n", ""})
  void parseBad(String v) {

    var e = assertThrows(Exception.class, () -> IdpListJWS.parse(v));

    assertEquals("failed to parse idp list", e.getMessage());
  }

  @Test
  void badType() {

    var raw = toB64("{\"typ\":\"?\"}") + "." + toB64("{}");

    var e = assertThrows(Exception.class, () -> IdpListJWS.parse(raw));
  }

  @Test
  void validAt() {

    var now = Instant.ofEpochSecond(1705942785);

    var nbf = now.minusSeconds(17);
    var exp = now.plusSeconds(391);

    var jws =
        new IdpListJWS(
            null, new IdpList(null, exp.getEpochSecond(), 0, nbf.getEpochSecond(), null));

    var afterExpiration = exp.plusSeconds(1);
    var beforeValidity = nbf.minusSeconds(1);

    // when & then
    assertTrue(jws.isValidAt(now));
    assertFalse(jws.isValidAt(afterExpiration));
    assertFalse(jws.isValidAt(beforeValidity));
  }
}
