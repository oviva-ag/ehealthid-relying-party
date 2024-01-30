package com.oviva.gesundheitsid.fedclient.api;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.stream.Stream;

public class JavaHttpClient implements HttpClient {

  private final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();

  @Override
  public Response call(Request req) {

    var builder = HttpRequest.newBuilder().uri(req.uri());

    Stream.ofNullable(req.headers())
        .flatMap(List::stream)
        .forEach(h -> builder.header(h.name(), h.value()));

    if (req.body() == null || req.body().length == 0) {
      builder.method(req.method(), BodyPublishers.noBody());
    } else {
      builder.method(req.method(), BodyPublishers.ofByteArray(req.body()));
    }

    try {
      var res = httpClient.send(builder.build(), BodyHandlers.ofByteArray());
      return toResponse(res);
    } catch (IOException e) {
      var msg = "failed to request '%s %s'".formatted(req.method(), req.uri());
      throw new RuntimeException(msg, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    return null;
  }

  private Response toResponse(HttpResponse<byte[]> response) {

    var headers =
        response.headers().map().entrySet().stream()
            .map(e -> new Header(e.getKey(), e.getValue().get(0)))
            .toList();

    return new Response(response.statusCode(), headers, response.body());
  }
}
