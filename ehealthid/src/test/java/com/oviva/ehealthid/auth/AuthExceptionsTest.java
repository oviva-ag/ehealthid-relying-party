package com.oviva.ehealthid.auth;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AuthExceptionsTest {

  @Test
  void invalidParRequestUri() {
    var got = AuthExceptions.invalidParRequestUri("https://example.com");
    assertEquals(AuthException.Reason.INVALID_PAR_URI, got.reason());
  }

  @Test
  void missingAuthorizationEndpoint() {
    var got = AuthExceptions.missingAuthorizationEndpoint("https://example.com");
    assertEquals(AuthException.Reason.MISSING_AUTHORIZATION_ENDPOINT, got.reason());
  }

  @Test
  void missingParEndpoint() {
    var got = AuthExceptions.missingParEndpoint("https://example.com");
    assertEquals(AuthException.Reason.MISSING_PAR_ENDPOINT, got.reason());
  }

  @Test
  void failedParRequest() {
    var cause = new IllegalArgumentException();
    var got = AuthExceptions.failedParRequest("https://fedmaster.example.com", cause);

    assertEquals(AuthException.Reason.FAILED_PAR_REQUEST, got.reason());
    assertEquals(cause, got.getCause());
  }

  @Test
  void missingOpenIdConfigurationInEntityStatement() {
    var got = AuthExceptions.missingOpenIdConfigurationInEntityStatement("https://example.com");

    assertEquals(
        AuthException.Reason.MISSING_OPENID_CONFIGURATION_IN_ENTITY_STATEMENT, got.reason());
  }

  @Test
  void badIdTokenSignature() {
    var got = AuthExceptions.badIdTokenSignature("https://example.com");
    assertEquals(AuthException.Reason.INVALID_ID_TOKEN, got.reason());
  }

  @Test
  void badIdToken() {
    var cause = new IllegalArgumentException();
    var got = AuthExceptions.badIdToken("https://example.com", cause);
    assertEquals(AuthException.Reason.INVALID_ID_TOKEN, got.reason());
    assertEquals(cause, got.getCause());
  }
}
