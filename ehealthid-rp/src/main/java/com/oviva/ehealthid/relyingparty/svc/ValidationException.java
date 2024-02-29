package com.oviva.ehealthid.relyingparty.svc;

import java.net.URI;

public class ValidationException extends RuntimeException {

  private final URI seeOther;

  public ValidationException(String message) {
    this(message, null, null);
  }

  public ValidationException(String message, URI seeOther) {
    this(message, null, seeOther);
  }

  public ValidationException(String message, Throwable cause, URI seeOther) {
    super(message, cause);
    this.seeOther = seeOther;
  }

  public URI seeOther() {
    return seeOther;
  }
}
