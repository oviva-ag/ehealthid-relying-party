package com.oviva.ehealthid.fedclient;

public class FederationException extends RuntimeException {

  public FederationException(String message) {
    super(message);
  }

  public FederationException(String message, Throwable cause) {
    super(message, cause);
  }
}
