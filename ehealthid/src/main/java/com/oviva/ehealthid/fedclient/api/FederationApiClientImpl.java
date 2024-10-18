package com.oviva.ehealthid.fedclient.api;

import com.oviva.ehealthid.fedclient.api.HttpClient.Header;
import com.oviva.ehealthid.fedclient.api.HttpClient.Request;
import edu.umd.cs.findbugs.annotations.NonNull;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriBuilder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class FederationApiClientImpl implements FederationApiClient {

  public static final String ENTITY_STATEMENT_MEDIA_TYPE = "application/entity-statement+jwt";
  public static final String SIGNED_JWKS_MEDIA_TYPE = "application/jwk-set+jwt";
  public static final String ALT_SIGNED_JWKS_MEDIA_TYPE = "application/jwk-set+json";
  public static final String WELLKNOWN_FEDERATION_DOCUMENT = "openid-federation";
  public static final String WELLKNOWN_PATH = ".well-known";

  private final HttpClient httpClient;

  public FederationApiClientImpl(HttpClient client) {
    this.httpClient = client;
  }

  @NonNull
  @Override
  public EntityStatementJWS fetchFederationStatement(
      URI federationFetchUrl, String issuer, String subject) {

    var params = List.of(new Param("iss", issuer), new Param("sub", subject));

    var body = doGetRequest(federationFetchUrl, ENTITY_STATEMENT_MEDIA_TYPE, params);
    return EntityStatementJWS.parse(body);
  }

  @NonNull
  @Override
  public IdpListJWS fetchIdpList(URI idpListUrl) {

    var body = doGetRequest(idpListUrl, MediaType.APPLICATION_JSON, null);
    return IdpListJWS.parse(body);
  }

  @Override
  public @NonNull EntityStatementJWS fetchEntityConfiguration(URI entityUrl) {

    var uri =
        UriBuilder.fromUri(entityUrl)
            .path(WELLKNOWN_PATH)
            .path(WELLKNOWN_FEDERATION_DOCUMENT)
            .build();

    var body = doGetRequest(uri, ENTITY_STATEMENT_MEDIA_TYPE, null);
    return EntityStatementJWS.parse(body);
  }

  @NonNull
  @Override
  public ExtendedJWKSetJWS fetchSignedJwks(URI signedJwksUrl) {

    // the Gematik IdP lies about the content-type, hence also requesting 'application/jwk-set+json'
    var body =
        doGetRequest(
            signedJwksUrl, SIGNED_JWKS_MEDIA_TYPE + ", " + ALT_SIGNED_JWKS_MEDIA_TYPE, null);
    return ExtendedJWKSetJWS.parse(body);
  }

  private String doGetRequest(URI uri, String accept, List<Param> params) {

    List<Header> headers = new ArrayList<>();

    headers.add(new Header(HttpHeaders.ACCEPT, accept));

    if (params != null) {
      var builder = UriBuilder.fromUri(uri);
      params.forEach(p -> builder.queryParam(p.name(), p.value()));
      uri = builder.build();
    }

    var req = new Request(uri, "GET", headers, null);

    var res = httpClient.call(req);
    if (res.status() != 200) {
      throw HttpExceptions.httpFailBadStatus("GET", uri, res.status());
    }

    return new String(res.body(), StandardCharsets.UTF_8);
  }

  private record Param(String name, String value) {}
}
