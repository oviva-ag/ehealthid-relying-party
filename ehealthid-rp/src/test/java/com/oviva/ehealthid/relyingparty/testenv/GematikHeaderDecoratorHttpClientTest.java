package com.oviva.ehealthid.relyingparty.testenv;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.oviva.ehealthid.fedclient.api.HttpClient;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class GematikHeaderDecoratorHttpClientTest {

  @Test
  void noDecoration() {

    var env = Map.of("GEMATIK_AUTH_HEADER", "myheader");
    Environment.getenv = env::get;

    var httpClient = mock(HttpClient.class);
    var client = new GematikHeaderDecoratorHttpClient(httpClient);

    var uri = URI.create("https://example.com");
    var req = new HttpClient.Request(uri, "GET", List.of(), null);

    var res = client.call(req);

    var captor = ArgumentCaptor.forClass(HttpClient.Request.class);
    verify(httpClient).call(captor.capture());

    var decoratedReq = captor.getValue();

    var hasAuthHeader =
        decoratedReq.headers().stream().anyMatch(h -> "X-Authorization".equals(h.name()));

    assertFalse(hasAuthHeader);
  }

  @Test
  void worksIfHeaderMissing() {

    var env = Map.of("GEMATIK_AUTH_HEADER", "");
    Environment.getenv = env::get;

    var httpClient = mock(HttpClient.class);
    var client = new GematikHeaderDecoratorHttpClient(httpClient);

    var uri = URI.create("https://gsi-ref-mtls.dev.gematik.solutions/PAR_Auth");
    var req = new HttpClient.Request(uri, "GET", List.of(), null);

    // when
    var res = client.call(req);

    // then
    var captor = ArgumentCaptor.forClass(HttpClient.Request.class);
    verify(httpClient).call(captor.capture());

    var decoratedReq = captor.getValue();

    var hasAuthHeader =
        decoratedReq.headers().stream().anyMatch(h -> "X-Authorization".equals(h.name()));

    assertFalse(hasAuthHeader);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://gsi-ref.dev.gematik.solutions/.well-known/openid-federation",
        "https://gsi-ref-mtls.dev.gematik.solutions/PAR_Auth",
        "https://gsi.dev.gematik.solutions/.well-known/openid-federation",
      })
  void decoratesRequest(String rawUri) {

    var authHeader = "mySpecialS3cr3t";
    var env = Map.of("GEMATIK_AUTH_HEADER", authHeader);
    Environment.getenv = env::get;

    var mockRes = mock(HttpClient.Response.class);
    var httpClient = mock(HttpClient.class);
    when(httpClient.call(any())).thenReturn(mockRes);
    var client = new GematikHeaderDecoratorHttpClient(httpClient);

    var uri = URI.create(rawUri);
    var req = new HttpClient.Request(uri, "GET", List.of(), null);

    // when
    var res = client.call(req);

    // then
    var captor = ArgumentCaptor.forClass(HttpClient.Request.class);
    verify(httpClient).call(captor.capture());

    var decoratedReq = captor.getValue();

    var header =
        decoratedReq.headers().stream()
            .filter(h -> "X-Authorization".equals(h.name()))
            .findFirst()
            .orElseThrow();

    assertEquals(authHeader, header.value());
    assertEquals(mockRes, res);
  }
}
