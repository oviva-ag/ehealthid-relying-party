package com.oviva.ehealthid.fedclient;

public class FederationException extends RuntimeException {

  private final Reason reason;

  public FederationException(String message, Reason reason) {
    this(message, null, reason);
  }

  public FederationException(String message, Throwable cause, Reason reason) {
    super(message, cause);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }

  public enum Reason {
    UNKNOWN,
    UNTRUSTED_IDP,
    INVALID_ENTITY_STATEMENT,
    BAD_FEDERATION_MASTER
  }
}
