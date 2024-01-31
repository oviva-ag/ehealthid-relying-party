package com.oviva.gesundheitsid.auth;

public class AuthExceptions {
  private AuthExceptions() {}

  public static RuntimeException invalidParRequestUri(String uri) {
    return new RuntimeException("invalid par request_uri '%s'".formatted(uri));
  }

  public static RuntimeException missingAuthorizationUrl(String sub) {
    return new RuntimeException(
        "entity statement of '%s' has no authorization url configuration".formatted(sub));
  }

  public static RuntimeException missingPARUrl(String sub) {
    return new RuntimeException(
        "entity statement of '%s' has no pushed authorization request configuration"
            .formatted(sub));
  }

  public static RuntimeException missingOpenIdConfigurationInEntityStatement(String sub) {
    return new RuntimeException(
        "entity statement of '%s' lacks openid configuration".formatted(sub));
  }

  public static RuntimeException badIdTokenSignature(String issuer) {
    return new RuntimeException("bad ID token signature from sub=%s".formatted(issuer));
  }

  public static RuntimeException badIdToken(String issuer, Exception cause) {
    return new RuntimeException("bad ID token from sub=%s".formatted(issuer), cause);
  }
}
