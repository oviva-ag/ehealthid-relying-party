package com.oviva.ehealthid.relyingparty.util;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.serviceUnavailable;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.RemoteKeySourceException;
import com.nimbusds.jose.jwk.source.JWKSetRetrievalException;
import java.net.URI;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;

@WireMockTest
class DiscoveryJwkSetSourceTest {
  private static final String DISCOVERY_PATH = "/.well-known/openid-configuration";
  private static final String JWKS_PATH = "/jwks";

  @Test
  void getJWKSet_success(WireMockRuntimeInfo wm) throws KeySourceException {

    var kid = "test";
    var jwks =
        """
    {
      "keys": [
        {
          "kty": "EC",
          "use": "sig",
          "crv": "P-256",
          "kid": "%s",
          "x": "PFjgWFHOCAtnw47F3bT99fmWOKcDARN45JGEPgB8yKs",
          "y": "sFR6D_6Pa1vRRc5OfQNsetnN8EkXNliEipaip2L2OBg"
        }
      ]
    }
    """
            .formatted(kid);

    var discoveryUrl = URI.create(wm.getHttpBaseUrl()).resolve(DISCOVERY_PATH);

    var jwksUrl = URI.create(wm.getHttpBaseUrl()).resolve(JWKS_PATH);

    stubFor(get(DISCOVERY_PATH).willReturn(okJson("{\"jwks_uri\": \"%s\"}".formatted(jwksUrl))));

    stubFor(get(JWKS_PATH).willReturn(okJson(jwks)));

    var sut = new DiscoveryJwkSetSource<>(HttpClient.newHttpClient(), discoveryUrl);

    var gotJwks = sut.getJWKSet(null, 0, null);
    assertNotNull(gotJwks.getKeyByKeyId(kid));
    assertEquals(1, gotJwks.getKeys().size());
  }

  @Test
  void getJWKSet_noKeys(WireMockRuntimeInfo wm) throws KeySourceException {

    var jwks = "{\"keys\": []}";

    var discoveryUrl = URI.create(wm.getHttpBaseUrl()).resolve(DISCOVERY_PATH);

    var jwksUrl = URI.create(wm.getHttpBaseUrl()).resolve(JWKS_PATH);

    stubFor(get(DISCOVERY_PATH).willReturn(okJson("{\"jwks_uri\": \"%s\"}".formatted(jwksUrl))));

    stubFor(get(JWKS_PATH).willReturn(okJson(jwks)));

    var sut = new DiscoveryJwkSetSource<>(HttpClient.newHttpClient(), discoveryUrl);

    var gotJwks = sut.getJWKSet(null, 0, null);
    assertTrue(gotJwks.getKeys().isEmpty());
  }

  @Test
  void getJWKSet_badCode(WireMockRuntimeInfo wm) {

    var discoveryUrl = URI.create(wm.getHttpBaseUrl()).resolve(DISCOVERY_PATH);

    var jwksUrl = URI.create(wm.getHttpBaseUrl()).resolve(JWKS_PATH);

    stubFor(get(DISCOVERY_PATH).willReturn(okJson("{\"jwks_uri\": \"%s\"}".formatted(jwksUrl))));

    stubFor(get(JWKS_PATH).willReturn(serviceUnavailable()));

    var sut = new DiscoveryJwkSetSource<>(HttpClient.newHttpClient(), discoveryUrl);

    assertThrows(JWKSetRetrievalException.class, () -> sut.getJWKSet(null, 0, null));
  }

  @Test
  void getJWKSet_badDiscoveryUrl() {

    var discoveryUrl = URI.create("http://badURi");

    var sut = new DiscoveryJwkSetSource<>(HttpClient.newHttpClient(), discoveryUrl);

    assertThrows(RemoteKeySourceException.class, () -> sut.getJWKSet(null, 0, null));
  }

  @Test
  void getJWKSet_badDiscoveryStatus(WireMockRuntimeInfo wm) {

    var discoveryUrl = URI.create(wm.getHttpBaseUrl()).resolve(DISCOVERY_PATH);

    stubFor(get(DISCOVERY_PATH).willReturn(serverError()));

    var sut = new DiscoveryJwkSetSource<>(HttpClient.newHttpClient(), discoveryUrl);

    assertThrows(RemoteKeySourceException.class, () -> sut.getJWKSet(null, 0, null));
  }

  @Test
  void getJWKSet_noJwksUri(WireMockRuntimeInfo wm) {

    var discoveryUrl = URI.create(wm.getHttpBaseUrl()).resolve(DISCOVERY_PATH);

    stubFor(get(DISCOVERY_PATH).willReturn(okJson("{\"jwks_uri\": null}")));

    var sut = new DiscoveryJwkSetSource<>(HttpClient.newHttpClient(), discoveryUrl);

    assertThrows(RemoteKeySourceException.class, () -> sut.getJWKSet(null, 0, null));
  }

  @Test
  void getJWKSet_badJwksUri(WireMockRuntimeInfo wm) {

    var discoveryUrl = URI.create(wm.getHttpBaseUrl()).resolve(DISCOVERY_PATH);

    var jwksUrl = "wut://bad?";

    stubFor(get(DISCOVERY_PATH).willReturn(okJson("{\"jwks_uri\": \"%s\"}".formatted(jwksUrl))));

    stubFor(get(JWKS_PATH).willReturn(serviceUnavailable()));

    var sut = new DiscoveryJwkSetSource<>(HttpClient.newHttpClient(), discoveryUrl);

    assertThrows(RemoteKeySourceException.class, () -> sut.getJWKSet(null, 0, null));
  }
}
