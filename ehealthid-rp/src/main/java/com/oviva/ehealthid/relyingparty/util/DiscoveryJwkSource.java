package com.oviva.ehealthid.relyingparty.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.RemoteKeySourceException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSetCacheRefreshEvaluator;
import com.nimbusds.jose.jwk.source.JWKSetSource;
import com.nimbusds.jose.jwk.source.URLBasedJWKSetSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.Resource;
import com.nimbusds.jose.util.ResourceRetriever;
import com.oviva.ehealthid.util.JsonCodec;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;

/** A JWK source that always fetches the latest JWKS from a given OpenID Discovery document URL */
public class DiscoveryJwkSource<T extends SecurityContext> implements JWKSetSource<T> {

  private final HttpClient httpClient;
  private final URI discoveryUrl;

  public DiscoveryJwkSource(HttpClient httpClient, URI discoveryUrl) {
    this.httpClient = httpClient;
    this.discoveryUrl = discoveryUrl;
  }

  @Override
  public JWKSet getJWKSet(JWKSetCacheRefreshEvaluator refreshEvaluator, long currentTime, T context)
      throws KeySourceException {
    var jwksUrl = discoverJwksUrl();
    try (var jwkSetSource = new URLBasedJWKSetSource<>(jwksUrl, new HttpRetriever(httpClient))) {
      return jwkSetSource.getJWKSet(null, 0, context);
    } catch (IOException e) {
      throw new RemoteKeySourceException(
          "failed to fetch jwks from discovery document '%s'".formatted(discoveryUrl), e);
    }
  }

  private URL discoverJwksUrl() throws RemoteKeySourceException {

    var req = HttpRequest.newBuilder(discoveryUrl).GET().build();

    try {
      var res = httpClient.send(req, BodyHandlers.ofByteArray());
      if (res.statusCode() != 200) {
        throw new RemoteKeySourceException(
            "bad status code fetching '%s' %d".formatted(discoveryUrl, res.statusCode()), null);
      }

      var body = JsonCodec.readValue(res.body(), DiscoveryDocument.class);

      if (body.jwksUri() == null) {
        throw new RemoteKeySourceException(
            "discovery document at '%s' lacks 'jwks_uri'".formatted(discoveryUrl), null);
      }

      return new URI(body.jwksUri()).toURL();
    } catch (URISyntaxException | MalformedURLException e) {
      throw new RemoteKeySourceException(
          "discovery document at '%s' has malformed 'jwks_uri'".formatted(discoveryUrl), e);
    } catch (IOException e) {
      throw new RemoteKeySourceException(
          "failed to fetch OpenID discovery document from '%s'".formatted(discoveryUrl), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("unreachable");
    }
  }

  @Override
  public void close() throws IOException {}

  record DiscoveryDocument(
      @JsonProperty("issuer") String issuer, @JsonProperty("jwks_uri") String jwksUri) {}

  static class HttpRetriever implements ResourceRetriever {

    private final HttpClient httpClient;

    HttpRetriever(HttpClient httpClient) {
      this.httpClient = httpClient;
    }

    @Override
    public Resource retrieveResource(URL url) throws IOException {
      try {
        var req = HttpRequest.newBuilder(url.toURI()).GET().build();
        var res = httpClient.send(req, BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) {
          throw new IOException(
              "bad status code fetching '%s': %d".formatted(url, res.statusCode()));
        }
        return new Resource(res.body(), res.headers().firstValue("content-type").orElse(null));
      } catch (URISyntaxException e) {
        throw new IOException("bad url", e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("unreachable");
      }
    }
  }
}
