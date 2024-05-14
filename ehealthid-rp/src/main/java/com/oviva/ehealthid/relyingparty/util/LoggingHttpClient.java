package com.oviva.ehealthid.relyingparty.util;

import com.oviva.ehealthid.fedclient.api.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingHttpClient implements HttpClient {

  private final Logger logger = LoggerFactory.getLogger(LoggingHttpClient.class);
  private final HttpClient delegate;

  public LoggingHttpClient(HttpClient delegate) {
    this.delegate = delegate;
  }

  @Override
  public Response call(Request req) {

    if (!logger.isDebugEnabled()) {
      return delegate.call(req);
    }

    logger
        .atDebug()
        .addKeyValue("url", () -> req.uri().toString())
        .addKeyValue(
            "headers",
            () ->
                req.headers().stream()
                    .map(h -> h.name() + ": " + h.value())
                    .collect(Collectors.joining("\n")))
        .addKeyValue("method", req::method)
        .addKeyValue(
            "body", () -> req.body() != null ? new String(req.body(), StandardCharsets.UTF_8) : "")
        .log("request: %s %s".formatted(req.method(), req.uri()));

    var res = delegate.call(req);

    logger
        .atDebug()
        .addKeyValue("url", () -> req.uri().toString())
        .addKeyValue("status", () -> Integer.toString(res.status()))
        .addKeyValue(
            "headers",
            () ->
                res.headers().stream()
                    .map(h -> h.name() + ": " + h.value())
                    .collect(Collectors.joining("\n")))
        .addKeyValue("method", req::method)
        .addKeyValue(
            "body", () -> res.body() != null ? new String(res.body(), StandardCharsets.UTF_8) : "")
        .log("response: %s %s %d".formatted(req.method(), req.uri(), res.status()));

    return res;
  }
}
