package com.oviva.ehealthid.relyingparty.ws;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.undertow.io.Sender;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import jakarta.ws.rs.core.Response.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MetricsEndpointTest {

  @Captor ArgumentCaptor<String> responseCaptor;

  @Test
  void get() {
    // when
    var httpServerExchange = mock(HttpServerExchange.class);
    var headers = mock(HeaderMap.class);
    var sender = mock(Sender.class);

    when(httpServerExchange.getResponseHeaders()).thenReturn(headers);
    when(httpServerExchange.getResponseSender()).thenReturn(sender);
    when(httpServerExchange.getRequestMethod()).thenReturn(HttpString.tryFromString("GET"));

    // given
    var prometheusMeterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    var counter = prometheusMeterRegistry.counter("test.counter");

    counter.increment(2.0);

    var sut = new MetricsEndpoint(prometheusMeterRegistry);

    // when
    sut.handleRequest(httpServerExchange);

    // then
    verify(sender).send(responseCaptor.capture());
    assertTrue(responseCaptor.getValue().contains("test_counter_total 2.0"));
  }

  @Test
  void methodNotAllowed() {
    // when
    var httpServerExchange = mock(HttpServerExchange.class);
    var sender = mock(Sender.class);

    when(httpServerExchange.getResponseSender()).thenReturn(sender);
    when(httpServerExchange.getRequestMethod()).thenReturn(HttpString.tryFromString("POST"));

    // given
    var prometheusMeterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    var sut = new MetricsEndpoint(prometheusMeterRegistry);

    // when
    sut.handleRequest(httpServerExchange);

    // then
    verify(httpServerExchange).setStatusCode(Status.METHOD_NOT_ALLOWED.getStatusCode());
  }
}
