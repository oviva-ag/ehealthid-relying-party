package com.oviva.ehealthid.auth;

public class AuthExceptions {
  private AuthExceptions() {}

  public static AuthException invalidParRequestUri(String uri) {
    // the field is called `request_uri`, hence the uri instead of url naming
    return new AuthException(
        "invalid par request_uri '%s'".formatted(uri), AuthException.Reason.INVALID_PAR_URI);
  }

  public static AuthException missingAuthorizationEndpoint(String sub) {
    return new AuthException(
        "entity statement of '%s' has no authorization endpoint configuration".formatted(sub),
        AuthException.Reason.MISSING_AUTHORIZATION_ENDPOINT);
  }

  public static AuthException missingParEndpoint(String sub) {
    return new AuthException(
        "entity statement of '%s' has no pushed authorization request endpoint configuration"
            .formatted(sub),
        AuthException.Reason.MISSING_PAR_ENDPOINT);
  }

  public static AuthException failedParRequest(String issuer, Exception cause) {
    return new AuthException(
        "PAR request failed sub=%s".formatted(issuer),
        cause,
        AuthException.Reason.FAILED_PAR_REQUEST);
  }

  public static AuthException missingOpenIdConfigurationInEntityStatement(String sub) {
    return new AuthException(
        "entity statement of '%s' lacks openid configuration".formatted(sub),
        AuthException.Reason.MISSING_OPENID_CONFIGURATION_IN_ENTITY_STATEMENT);
  }

  public static AuthException badIdTokenSignature(String issuer) {
    return new AuthException(
        "bad ID token signature from sub=%s".formatted(issuer),
        AuthException.Reason.INVALID_ID_TOKEN);
  }

  public static AuthException badIdToken(String issuer, Exception cause) {
    return new AuthException(
        "bad ID token from sub=%s".formatted(issuer), cause, AuthException.Reason.INVALID_ID_TOKEN);
  }
}
