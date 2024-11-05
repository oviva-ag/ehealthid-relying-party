package com.oviva.ehealthid.relyingparty.testenv;

import com.oviva.ehealthid.fedclient.api.HttpClient;
import java.util.ArrayList;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GematikHeaderDecoratorHttpClient implements HttpClient {

  private static final Logger logger =
      LoggerFactory.getLogger(GematikHeaderDecoratorHttpClient.class);

  // RU: https://gsi-ref.dev.gematik.solutions/.well-known/openid-federation
  // RU PAR mTLS: https://gsi-ref-mtls.dev.gematik.solutions/PAR_Auth
  // TU: https://gsi.dev.gematik.solutions/.well-known/openid-federation
  private static final Pattern HOST_GEMATIK_IDP =
      Pattern.compile("gsi(-[-a-z0-9]+)?.dev.gematik.solutions");
  private final HttpClient delegate;

  public GematikHeaderDecoratorHttpClient(HttpClient delegate) {
    this.delegate = delegate;
  }

  @Override
  public Response call(Request req) {

    if (HOST_GEMATIK_IDP.matcher(req.uri().getHost()).matches()) {
      if (Environment.gematikAuthHeader() == null || Environment.gematikAuthHeader().isBlank()) {
        logger.warn(
            "missing 'GEMATIK_AUTH_HEADER' environment value against '{}'", req.uri().getHost());
        return delegate.call(req);
      }

      var headers = new ArrayList<>(req.headers());
      headers.add(new Header("X-Authorization", Environment.gematikAuthHeader()));

      req = new Request(req.uri(), req.method(), headers, req.body());
    }

    return delegate.call(req);
  }
}
