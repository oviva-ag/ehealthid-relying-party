package com.oviva.ehealthid.relyingparty.ws;

import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;

public class MetricsEndpoint implements HttpHandler {
  public static final String PATH = "/metrics";
  private static final int HTTP_OK = 200;
  private static final int HTTP_METHOD_NOT_ALLOWED = 405;

  private final PrometheusMeterRegistry registry;

  public MetricsEndpoint(PrometheusMeterRegistry registry) {
    this.registry = registry;

    new ClassLoaderMetrics().bindTo(this.registry);
    new JvmMemoryMetrics().bindTo(this.registry);
    new JvmGcMetrics().bindTo(this.registry);
    new ProcessorMetrics().bindTo(this.registry);
    new JvmThreadMetrics().bindTo(this.registry);
  }

  @Override
  public void handleRequest(HttpServerExchange httpServerExchange) {
    if (!httpServerExchange.getRequestMethod().equals(HttpString.tryFromString("GET"))) {
      httpServerExchange.setStatusCode(HTTP_METHOD_NOT_ALLOWED);
      httpServerExchange.getResponseSender().send("");
    } else {
      httpServerExchange.setStatusCode(HTTP_OK);
      httpServerExchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");

      var metricsContents = registry.scrape();
      httpServerExchange.getResponseSender().send(metricsContents);
    }
  }
}
