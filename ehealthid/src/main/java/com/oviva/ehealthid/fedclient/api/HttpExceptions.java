package com.oviva.ehealthid.fedclient.api;

import java.net.URI;

public class HttpExceptions {

  private HttpExceptions() {}

  public static HttpException httpFailBadStatus(String method, URI uri, int status) {
    return new HttpException(status, method, uri, "bad status");
  }

  public static HttpException httpFailCausedBy(String method, URI uri, Exception cause) {
    return new HttpException(method, uri, "http request failed", cause);
  }
}
