package com.oviva.ehealthid.relyingparty.svc;

import java.net.URI;

public class ValidationException extends RuntimeException {

  private final URI seeOther;

  private final LocalizedErrorMessage localizedErrorMessage;

  public ValidationException(String message) {
    this(message, null, null, null);
  }

  public ValidationException(LocalizedErrorMessage localizedErrorMessage, URI seeOther) {
    this(localizedErrorMessage.messageKey, null, seeOther, null);
  }

  public ValidationException(LocalizedErrorMessage localizedErrorMessage) {
    this(localizedErrorMessage.messageKey, null, null, localizedErrorMessage);
  }

  public ValidationException(
      String message, Throwable cause, URI seeOther, LocalizedErrorMessage localizedErrorMessage) {
    super(message, cause);
    this.seeOther = seeOther;
    this.localizedErrorMessage = localizedErrorMessage;
  }

  public URI seeOther() {
    return seeOther;
  }

  public LocalizedErrorMessage getLocalizedErrorMessage() {
    return localizedErrorMessage;
  }

  public record LocalizedErrorMessage(String messageKey, String additionalInfo) {}
}
