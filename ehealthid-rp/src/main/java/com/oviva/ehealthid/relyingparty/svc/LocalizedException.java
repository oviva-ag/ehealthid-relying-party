package com.oviva.ehealthid.relyingparty.svc;

public interface LocalizedException {

  Message localizedMessage();

  record Message(String messageKey, String... args) {}
}
