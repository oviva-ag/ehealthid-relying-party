package com.oviva.ehealthid.relyingparty.svc;

import java.net.URI;

public class ValidationException extends RuntimeException implements LocalizedException {

  private final URI seeOther;

  private final Message localizedMessage;

  public ValidationException(String message) {
    this(message, null, null, null);
  }

  public ValidationException(Message localizedMessage, URI seeOther) {
    this(localizedMessage.messageKey(), null, seeOther, null);
  }

  public ValidationException(Message localizedMessage) {
    this(localizedMessage.messageKey(), null, null, localizedMessage);
  }

  public ValidationException(
      String message, Throwable cause, URI seeOther, Message localizedMessage) {
    super(message, cause);
    this.seeOther = seeOther;
    this.localizedMessage = localizedMessage;
  }

  public URI seeOther() {
    return seeOther;
  }

  @Override
  public LocalizedException.Message localizedMessage() {
    return localizedMessage;
  }
}
