package com.oviva.gesundheitsid.fedclient.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.oviva.gesundheitsid.fedclient.api.HttpClient.Header;
import com.oviva.gesundheitsid.fedclient.api.HttpClient.Request;
import com.oviva.gesundheitsid.util.JsonCodec;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import java.net.URI;
import java.util.List;

public class OpenIdClient {

  private final HttpClient httpClient;

  public OpenIdClient(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  public TokenResponse exchangePkceCode(
      URI tokenEndpoint, String code, String redirectUri, String clientId, String codeVerifier) {

    var body =
        UrlFormBodyBuilder.create()
            .param("grant_type", "authorization_code")
            .param("redirect_uri", redirectUri)
            .param("client_id", clientId)
            .param("code", code)
            .param("code_verifier", codeVerifier)
            .build();

    var headers =
        List.of(
            new Header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON),
            new Header(HttpHeaders.CONTENT_TYPE, UrlFormBodyBuilder.MEDIA_TYPE));

    var req = new Request(tokenEndpoint, "POST", headers, body);

    var res = httpClient.call(req);
    if (res.status() != 200) {
      throw HttpExceptions.httpFailBadStatus(req.method(), tokenEndpoint, res.status());
    }

    return JsonCodec.readValue(res.body(), TokenResponse.class);
  }

  public ParResponse requestPushedUri(
      URI pushedAuthorizationRequestUri, ParBodyBuilder parBodyBuilder) {

    var headers =
        List.of(
            new Header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON),
            new Header(HttpHeaders.CONTENT_TYPE, UrlFormBodyBuilder.MEDIA_TYPE));

    var req = new Request(pushedAuthorizationRequestUri, "POST", headers, parBodyBuilder.build());

    var res = httpClient.call(req);
    if (res.status() != 201) {
      throw HttpExceptions.httpFailBadStatus(
          req.method(), pushedAuthorizationRequestUri, res.status());
    }

    return JsonCodec.readValue(res.body(), ParResponse.class);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ParResponse(
      @JsonProperty("request_uri") String requestUri, @JsonProperty("expires_in") long expiresIn) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("expires_in") long expiresIn,
      @JsonProperty("id_token") String idToken,
      @JsonProperty("token_type") String tokenType,
      @JsonProperty("refresh_token") String refreshToken) {}
}
