package com.oviva.ehealthid.relyingparty.ws;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

public class HealthEndpoint implements HttpHandler {
  public static final String PATH = "/health";

  private static final int HTTP_METHOD_NOT_ALLOWED = 405;
  private static final int HTTP_OK = 200;

  private static final String STATUS_UP = "{\"status\":\"UP\"}";

  @Override
  public void handleRequest(HttpServerExchange httpServerExchange) {
    if (!httpServerExchange.getRequestMethod().equals(HttpString.tryFromString("GET"))) {
      httpServerExchange.setStatusCode(HTTP_METHOD_NOT_ALLOWED);
      httpServerExchange.getResponseSender().send("");
    } else {
      // For now if this endpoint is reachable then the service is up.
      // There is no hard dependency that could be down.
      httpServerExchange.setStatusCode(HTTP_OK);
      httpServerExchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
      httpServerExchange.getResponseSender().send(STATUS_UP);
    }
  }
}
