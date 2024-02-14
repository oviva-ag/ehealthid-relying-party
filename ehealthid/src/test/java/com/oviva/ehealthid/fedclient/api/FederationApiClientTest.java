package com.oviva.ehealthid.fedclient.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.badRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.http.ContentTypeHeader;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.oviva.ehealthid.test.Fixtures;
import com.oviva.ehealthid.test.GematikHeaderDecoratorHttpClient;
import jakarta.ws.rs.core.UriBuilder;
import java.net.URI;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@WireMockTest
class FederationApiClientTest {

  private final HttpClient javaHttpClient =
      new JavaHttpClient(java.net.http.HttpClient.newHttpClient());

  private static final String MEDIA_TYPE_ENTITY_STATEMENT =
      "application/entity-statement+jwt;charset=UTF-8";

  @Test
  @Disabled("e2e")
  void fetchReferenceEnvironment() {

    var apiClient = new FederationApiClientImpl(javaHttpClient);

    var federationMaster = "https://app-ref.federationmaster.de";

    // when fetching fedmaster entity configuration
    var jws = apiClient.fetchEntityConfiguration(URI.create(federationMaster));
    var masterEntityStatement = jws.body();

    // and listing available idps
    var idps =
        apiClient.fetchIdpList(
            URI.create(masterEntityStatement.metadata().federationEntity().idpListEndpoint()));

    var idp = idps.body().idpEntities().get(0).iss();

    // and fetching their federation statement with the master
    var idpEntityStatement =
        apiClient.fetchFederationStatement(
            URI.create(
                masterEntityStatement.metadata().federationEntity().federationFetchEndpoint()),
            federationMaster,
            idp);

    // then
    assertEquals(federationMaster, idpEntityStatement.body().iss());
    assertEquals(idp, idpEntityStatement.body().sub());
  }

  @Test
  @Disabled("e2e")
  void fetchTuGematikIdp() {
    // we need an extra header for the test environment
    var httpClient = new GematikHeaderDecoratorHttpClient(javaHttpClient);

    var apiClient = new FederationApiClientImpl(httpClient);

    // the Gematik test IDP
    var gematikIdp = "https://gsi.dev.gematik.solutions";

    var jws = apiClient.fetchEntityConfiguration(URI.create(gematikIdp));

    var masterEntityStatement = jws.body();
    assertEquals(gematikIdp, masterEntityStatement.iss());
    assertEquals(gematikIdp, masterEntityStatement.sub());
  }

  @Test
  void fetchFederationStatement(WireMockRuntimeInfo wm) {

    var federationListPath = "/federation/list";

    var sub = "https://idp-test.oviva.io/auth/realms/master/ehealthid";
    var iss = "https://app-test.federationmaster.de";

    var body = Fixtures.get("federation_api_client_federationStatement.txt");

    var uri =
        UriBuilder.fromUri(federationListPath)
            .queryParam("iss", iss)
            .queryParam("sub", sub)
            .build();

    stubFor(
        get(uri.toString())
            .willReturn(
                aResponse()
                    .withHeader(ContentTypeHeader.KEY, MEDIA_TYPE_ENTITY_STATEMENT)
                    .withBody(body)));

    var client = new FederationApiClientImpl(javaHttpClient);

    var federationMaster = wm.getHttpBaseUrl();

    var jws =
        client.fetchFederationStatement(
            URI.create(federationMaster).resolve(federationListPath), iss, sub);

    assertEquals(jws.body().iss(), iss);
    assertEquals(jws.body().sub(), sub);
    assertThat(jws.body().jwks().getKeys(), hasSize(1));
  }

  @Test
  void fetchIdpList(WireMockRuntimeInfo wm) {

    var idpListPath = "/federation/listidps";

    var body = Fixtures.get("federation_api_client_idpList.txt");

    var ct = "application/jwt;charset=UTF-8";
    stubFor(
        get(idpListPath)
            .willReturn(aResponse().withHeader(ContentTypeHeader.KEY, ct).withBody(body)));

    var client = new FederationApiClientImpl(javaHttpClient);

    var federationMaster = wm.getHttpBaseUrl();

    var jws = client.fetchIdpList(URI.create(federationMaster).resolve(idpListPath));

    assertEquals("https://app-ref.federationmaster.de", jws.body().iss());
    assertThat(jws.body().idpEntities(), hasSize(23));
  }

  @Test
  void fetchEntityStatementError(WireMockRuntimeInfo wm) {

    var openidFederationPath = "/.well-known/openid-federation";

    stubFor(
        get(openidFederationPath)
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

    var client = new FederationApiClientImpl(javaHttpClient);

    var federationMaster = wm.getHttpBaseUrl();

    var e =
        assertThrows(
            HttpException.class,
            () -> client.fetchEntityConfiguration(URI.create(federationMaster)));

    var expected =
        "http request failed: http request failed 'GET %s'"
            .formatted(federationMaster + openidFederationPath);

    assertEquals(expected, e.getMessage());
    assertNotNull(e.getCause());
  }

  @Test
  void fetchEntityStatementBadRequest(WireMockRuntimeInfo wm) {

    var openidFederationPath = "/.well-known/openid-federation";

    stubFor(get(openidFederationPath).willReturn(badRequest()));

    var client = new FederationApiClientImpl(javaHttpClient);

    var federationMaster = wm.getHttpBaseUrl();

    var e =
        assertThrows(
            HttpException.class,
            () -> client.fetchEntityConfiguration(URI.create(federationMaster)));

    var expected =
        "http request failed: bad status 'GET %s' status=400"
            .formatted(federationMaster + openidFederationPath);

    assertEquals(expected, e.getMessage());
  }

  @Test
  void fetchEntityStatement(WireMockRuntimeInfo wm) {

    var openidFederationPath = "/.well-known/openid-federation";

    var body = Fixtures.get("federation_api_client_masterEntityStatement.txt");

    stubFor(
        get(openidFederationPath)
            .willReturn(
                aResponse()
                    .withHeader(ContentTypeHeader.KEY, MEDIA_TYPE_ENTITY_STATEMENT)
                    .withBody(body)));

    var client = new FederationApiClientImpl(javaHttpClient);

    var federationMaster = wm.getHttpBaseUrl();

    var jws = client.fetchEntityConfiguration(URI.create(federationMaster));

    assertEquals("https://app-ref.federationmaster.de", jws.body().iss());
    assertEquals("https://app-ref.federationmaster.de", jws.body().sub());

    assertEquals(
        "https://app-ref.federationmaster.de/federation/fetch",
        jws.body().metadata().federationEntity().federationFetchEndpoint());
    assertEquals(
        "https://app-ref.federationmaster.de/federation/listidps",
        jws.body().metadata().federationEntity().idpListEndpoint());

    assertTrue(jws.verifySelfSigned());
  }
}
