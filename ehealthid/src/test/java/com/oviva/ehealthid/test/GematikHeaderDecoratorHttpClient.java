package com.oviva.ehealthid.test;

import com.oviva.ehealthid.fedclient.api.HttpClient;
import java.util.ArrayList;

public class GematikHeaderDecoratorHttpClient implements HttpClient {

  private static final String HOST_GEMATIK_IDP = "gsi.dev.gematik.solutions";
  private final HttpClient delegate;

  public GematikHeaderDecoratorHttpClient(HttpClient delegate) {
    this.delegate = delegate;
  }

  @Override
  public Response call(Request req) {

    if (req.uri().getHost().equals(HOST_GEMATIK_IDP)) {
      if (Environment.gematikAuthHeader() == null || Environment.gematikAuthHeader().isBlank()) {
        throw new RuntimeException(
            "missing 'GEMATIK_AUTH_HEADER' environment value against '%s'"
                .formatted(HOST_GEMATIK_IDP));
      }

      var headers = new ArrayList<>(req.headers());
      headers.add(new Header("X-Authorization", Environment.gematikAuthHeader()));

      req = new Request(req.uri(), req.method(), headers, req.body());
    }

    return delegate.call(req);
  }
}
