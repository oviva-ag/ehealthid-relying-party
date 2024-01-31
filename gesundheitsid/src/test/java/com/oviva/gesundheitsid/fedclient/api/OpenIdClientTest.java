package com.oviva.gesundheitsid.fedclient.api;

import static com.github.tomakehurst.wiremock.client.WireMock.and;
import static com.github.tomakehurst.wiremock.client.WireMock.badRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.created;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

@WireMockTest
class OpenIdClientTest {

  private final OpenIdClient client = new OpenIdClient(new JavaHttpClient());

  @Test
  void exchangePkceCode(WireMockRuntimeInfo wm) {

    var body =
        """
        {
          "access_token" : null,
          "token_type"   : "Bearer",
          "expires_in"   : 3600,
          "id_token"     : "eyJraWQiOiIxZTlnZGs3IiwiYWxnIjoiUl..."
        }
        """
            .getBytes(StandardCharsets.UTF_8);

    var path = "/auth/token";
    stubFor(post(path).willReturn(ok().withBody(body)));

    var base = URI.create(wm.getHttpBaseUrl());

    var code = "s3cret";
    var codeVerifier = "k3k3k";
    var clientId = "myclient";
    var redirectUri = "http://localhost:8080/callback";

    var res =
        client.exchangePkceCode(base.resolve(path), code, redirectUri, clientId, codeVerifier);

    assertEquals("Bearer", res.tokenType());
    assertEquals(3600, res.expiresIn());
    assertThat(res.idToken(), not(emptyOrNullString()));
  }

  @Test
  void exchangePkceCode_badStatus(WireMockRuntimeInfo wm) {
    var path = "/auth/token";
    stubFor(post(path).willReturn(badRequest()));

    var base = URI.create(wm.getHttpBaseUrl());

    var e =
        assertThrows(
            Exception.class,
            () -> client.exchangePkceCode(base.resolve(path), null, null, null, null));

    assertEquals(
        "failed to request 'POST %s/auth/token': bad status 400".formatted(wm.getHttpBaseUrl()),
        e.getMessage());
  }

  @Test
  void requestPushedUri(WireMockRuntimeInfo wm) {

    var body =
        """
        {
          "request_uri":"https://example.com/auth/login",
          "expires_in": 600
        }
        """
            .getBytes(StandardCharsets.UTF_8);

    var path = "/auth/par";
    stubFor(post(path).willReturn(created().withBody(body)));

    var base = URI.create(wm.getHttpBaseUrl());

    var res = client.requestPushedUri(base.resolve(path), ParBodyBuilder.create());

    assertEquals("https://example.com/auth/login", res.requestUri());
    assertEquals(600L, res.expiresIn());
  }

  @Test
  void requestPushedUri_params(WireMockRuntimeInfo wm) {

    var response =
        """
        {"request_uri":"https://example.com/auth/login","expires_in": 600}
        """
            .getBytes(StandardCharsets.UTF_8);

    var path = "/auth/par";
    stubFor(post(path).willReturn(created().withBody(response)));

    var base = URI.create(wm.getHttpBaseUrl());

    var clientId = "test-client";
    var acrValues = "very-very-high";
    var codeChallenge = "myChallenge";
    var scopes = List.of("email", "openid");

    var body =
        ParBodyBuilder.create()
            .acrValues(acrValues)
            .codeChallenge(codeChallenge)
            .scopes(scopes)
            .clientId(clientId);

    // when
    client.requestPushedUri(base.resolve(path), body);

    // then
    verify(
        postRequestedFor(urlPathEqualTo(path))
            .withRequestBody(
                and(
                    containing("client_id=" + clientId),
                    containing("acr_values=" + acrValues),
                    containing("code_challenge=" + codeChallenge),
                    containing("scope=" + String.join("+", scopes)))));
  }

  @Test
  void requestPushedUri_badStatus(WireMockRuntimeInfo wm) {
    var path = "/auth/par";
    stubFor(post(path).willReturn(badRequest()));

    var base = URI.create(wm.getHttpBaseUrl());

    var e =
        assertThrows(
            Exception.class,
            () -> client.requestPushedUri(base.resolve(path), ParBodyBuilder.create()));

    assertEquals(
        "failed to request 'POST %s/auth/par': bad status 400".formatted(wm.getHttpBaseUrl()),
        e.getMessage());
  }
}
