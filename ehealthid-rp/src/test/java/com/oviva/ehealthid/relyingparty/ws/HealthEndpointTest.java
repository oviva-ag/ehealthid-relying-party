package com.oviva.ehealthid.relyingparty.ws;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.undertow.io.Sender;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import jakarta.ws.rs.core.Response.Status;
import org.junit.jupiter.api.Test;

class HealthEndpointTest {

  @Test
  void get() {
    var sut = new HealthEndpoint();

    // when
    var httpServerExchange = mock(HttpServerExchange.class);
    var headers = mock(HeaderMap.class);
    var sender = mock(Sender.class);

    when(httpServerExchange.getResponseHeaders()).thenReturn(headers);
    when(httpServerExchange.getResponseSender()).thenReturn(sender);
    when(httpServerExchange.getRequestMethod()).thenReturn(HttpString.tryFromString("GET"));

    sut.handleRequest(httpServerExchange);

    // then
    verify(httpServerExchange).setStatusCode(Status.OK.getStatusCode());
  }

  @Test
  void methodNotAllowed() {
    var sut = new HealthEndpoint();

    // when
    var httpServerExchange = mock(HttpServerExchange.class);
    var sender = mock(Sender.class);

    when(httpServerExchange.getResponseSender()).thenReturn(sender);
    when(httpServerExchange.getRequestMethod()).thenReturn(HttpString.tryFromString("POST"));

    sut.handleRequest(httpServerExchange);

    // then
    verify(httpServerExchange).setStatusCode(Status.METHOD_NOT_ALLOWED.getStatusCode());
  }
}
