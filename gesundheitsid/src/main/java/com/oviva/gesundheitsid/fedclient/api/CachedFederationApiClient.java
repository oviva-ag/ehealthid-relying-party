package com.oviva.gesundheitsid.fedclient.api;

import java.net.URI;

/** very primitive cached client, there is no cache eviction here */
public class CachedFederationApiClient implements FederationApiClient {

  private final FederationApiClient delegate;

  private final Cache<EntityStatementJWS> entityStatementCache;

  private final Cache<EntityStatementJWS> federationStatementCache;

  private final Cache<IdpListJWS> idpListCache;

  public CachedFederationApiClient(
      FederationApiClient delegate,
      Cache<EntityStatementJWS> entityStatementCache,
      Cache<EntityStatementJWS> federationStatementCache,
      Cache<IdpListJWS> idpListCache) {
    this.delegate = delegate;
    this.entityStatementCache = entityStatementCache;
    this.federationStatementCache = federationStatementCache;
    this.idpListCache = idpListCache;
  }

  @Override
  public EntityStatementJWS fetchFederationStatement(
      URI federationFetchUrl, String issuer, String subject) {
    var key = "%s|%s|%s".formatted(federationFetchUrl, issuer, subject);
    return federationStatementCache.computeIfAbsent(
        key, k -> delegate.fetchFederationStatement(federationFetchUrl, issuer, subject));
  }

  @Override
  public IdpListJWS fetchIdpList(URI idpListUrl) {
    return idpListCache.computeIfAbsent(
        idpListUrl.toString(), k -> delegate.fetchIdpList(idpListUrl));
  }

  @Override
  public EntityStatementJWS fetchEntityConfiguration(URI entityUrl) {
    return entityStatementCache.computeIfAbsent(
        entityUrl.toString(), k -> delegate.fetchEntityConfiguration(entityUrl));
  }
}
