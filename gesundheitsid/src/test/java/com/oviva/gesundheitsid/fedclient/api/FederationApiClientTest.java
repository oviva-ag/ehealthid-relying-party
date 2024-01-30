package com.oviva.gesundheitsid.fedclient.api;

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
import com.oviva.gesundheitsid.test.Fixtures;
import com.oviva.gesundheitsid.test.GematikHeaderDecoratorHttpClient;
import jakarta.ws.rs.core.UriBuilder;
import java.net.URI;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@WireMockTest
class FederationApiClientTest {

  private static final String MEDIA_TYPE_ENTITY_STATEMENT =
      "application/entity-statement+jwt;charset=UTF-8";

  @Test
  @Disabled("e2e")
  void fetchReferenceEnvironment() {
    //    var httpClient = new GematikHeaderDecoratorHttpClient(new JavaHttpClient());
    var httpClient = new JavaHttpClient();

    var client = new FederationApiClientImpl(httpClient);

    var federationMaster = "https://app-ref.federationmaster.de";

    var jws = client.fetchEntityConfiguration(URI.create(federationMaster));
    System.out.println(jws);

    var masterEntityStatement = jws.body();

    var idps =
        client.fetchIdpList(
            URI.create(masterEntityStatement.metadata().federationEntity().idpListEndpoint()));
    System.out.println(idps);

    var idp = idps.body().idpEntities().get(0).iss();

    var idpEntityStatement =
        client.fetchFederationStatement(
            URI.create(
                masterEntityStatement.metadata().federationEntity().federationFetchEndpoint()),
            federationMaster,
            idp);
    System.out.println(idpEntityStatement);
  }

  @Test
  @Disabled("e2e")
  void fetchTuGematikIdp() {
    var httpClient = new GematikHeaderDecoratorHttpClient(new JavaHttpClient());
    //    var httpClient = new JavaHttpClient();

    var client = new FederationApiClientImpl(httpClient);

    var gematikIdp = "https://gsi.dev.gematik.solutions";

    var jws = client.fetchEntityConfiguration(URI.create(gematikIdp));
    System.out.println(jws);

    var masterEntityStatement = jws.body();
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

    var httpClient = new JavaHttpClient();

    var client = new FederationApiClientImpl(httpClient);

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

    var httpClient = new JavaHttpClient();

    var client = new FederationApiClientImpl(httpClient);

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

    var httpClient = new JavaHttpClient();

    var client = new FederationApiClientImpl(httpClient);

    var federationMaster = wm.getHttpBaseUrl();

    var e =
        assertThrows(
            Exception.class, () -> client.fetchEntityConfiguration(URI.create(federationMaster)));

    var expected = "failed to request 'GET %s'".formatted(federationMaster + openidFederationPath);

    assertEquals(expected, e.getMessage());
    assertNotNull(e.getCause());
  }

  @Test
  void fetchEntityStatementBadRequest(WireMockRuntimeInfo wm) {

    var openidFederationPath = "/.well-known/openid-federation";

    stubFor(get(openidFederationPath).willReturn(badRequest()));

    var httpClient = new JavaHttpClient();

    var client = new FederationApiClientImpl(httpClient);

    var federationMaster = wm.getHttpBaseUrl();

    var e =
        assertThrows(
            Exception.class, () -> client.fetchEntityConfiguration(URI.create(federationMaster)));

    var expected =
        "failed to request 'GET %s': bad status 400"
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

    var httpClient = new JavaHttpClient();

    var client = new FederationApiClientImpl(httpClient);

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

    System.out.println(jws);
  }
}
