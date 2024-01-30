package com.oviva.gesundheitsid.fedclient.api;

import java.net.URI;

public class HttpExceptions {

  private HttpExceptions() {}

  public static RuntimeException httpFailBadStatus(String method, URI uri, int status) {
    return httpFail(method, uri, "bad status %d".formatted(status));
  }

  public static RuntimeException httpFail(String method, URI uri, String reason) {
    return new RuntimeException("failed to request '%s %s': %s".formatted(method, uri, reason));
  }

  public static RuntimeException httpFailCausedBy(String method, URI uri, Exception cause) {
    return new RuntimeException("failed to request '%s %s'".formatted(method, uri), cause);
  }
}
