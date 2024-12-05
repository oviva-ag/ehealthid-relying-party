package com.oviva.ehealthid.auth;

public class AuthException extends RuntimeException {

  private final Reason reason;

  public AuthException(String message, Reason reason) {
    super(message);
    this.reason = reason;
  }

  public AuthException(String message, Throwable cause, Reason reason) {
    super(message, cause);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }

  public enum Reason {
    UNKNOWN,
    INVALID_PAR_URI,
    MISSING_PAR_ENDPOINT,
    FAILED_PAR_REQUEST,
    MISSING_AUTHORIZATION_ENDPOINT,
    MISSING_OPENID_CONFIGURATION_IN_ENTITY_STATEMENT,
    INVALID_ID_TOKEN
  }
}
