package com.oviva.ehealthid.fedclient;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.oviva.ehealthid.fedclient.api.EntityStatement;
import com.oviva.ehealthid.fedclient.api.EntityStatementJWS;
import com.oviva.ehealthid.fedclient.api.FederationApiClient;
import com.oviva.ehealthid.fedclient.api.IdpList.IdpEntity;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class FederationMasterClientImpl implements FederationMasterClient {

  private final URI fedMasterUri;
  private final FederationApiClient apiClient;

  private final Clock clock;

  public FederationMasterClientImpl(URI fedMasterUri, FederationApiClient apiClient, Clock clock) {
    this.fedMasterUri = fedMasterUri;
    this.apiClient = apiClient;
    this.clock = clock;
  }

  @Override
  public List<IdpEntry> listAvailableIdps() {

    var entities = mustFetchIdpList();

    return entities.stream()
        .map(e -> new IdpEntry(e.iss(), e.organizationName(), e.logoUri()))
        .toList();
  }

  @Override
  public JWKSet resolveOpenIdProviderJwks(@NonNull EntityStatementJWS es) {

    // https://openid.net/specs/openid-federation-1_0.html#section-5.2.1.1
    // https://gemspec.gematik.de/docs/gemSpec/gemSpec_IDP_Sek/latest/#A_22655-02

    var op =
        Optional.of(es)
            .map(EntityStatementJWS::body)
            .map(EntityStatement::metadata)
            .map(EntityStatement.Metadata::openidProvider);

    if (op.isEmpty()) {
      throw FederationExceptions.missingOpenIdProvider(es.body().sub());
    }

    List<JWK> allKeys = new ArrayList<>();

    // embedded keys
    op.map(EntityStatement.OpenidProvider::jwks).map(JWKSet::getKeys).ifPresent(allKeys::addAll);

    // from signed_jwks_uri
    op.map(EntityStatement.OpenidProvider::signedJwksUri)
        .flatMap(
            u -> fetchOpenIdProviderJwksFromSignedJwksUri(es.body().sub(), u, es.body().jwks()))
        .ifPresent(allKeys::addAll);

    // Note: OpenID federation also supports a `jwks_uri`, the GesundheitsID does not though
    if (allKeys.isEmpty()) {
      throw FederationExceptions.noOpenIdProviderKeys(es.body().sub());
    }

    return new JWKSet(allKeys);
  }

  @NonNull
  private Optional<List<JWK>> fetchOpenIdProviderJwksFromSignedJwksUri(
      @NonNull String issuer, @NonNull String signedJwksUri, @NonNull JWKSet idpTrustStore) {

    return Optional.of(signedJwksUri)
        .map(URI::create)
        .map(apiClient::fetchSignedJwks)
        .map(
            jws -> {
              if (!jws.isValidAt(clock.instant())) {
                throw FederationExceptions.expiredSignedJwks(issuer, signedJwksUri);
              }

              if (!jws.verifySignature(idpTrustStore)) {
                throw FederationExceptions.invalidSignedJwks(issuer, signedJwksUri);
              }

              if (!matchesIfPresent(issuer, jws.body().iss())) {
                throw FederationExceptions.invalidSignedJwks(issuer, signedJwksUri);
              }
              return jws;
            })
        .map(s -> s.body().toJWKSet())
        .map(JWKSet::getKeys);
  }

  private boolean matchesIfPresent(String expected, String actual) {
    if (actual == null || actual.isEmpty()) {
      return true;
    }

    return expected.equals(actual);
  }

  @Override
  public EntityStatementJWS establishIdpTrust(URI issuer) {

    var trustedFederationStatement = fetchTrustedFederationStatement(issuer);

    // the federation statement from the master will establish trust in the JWKS and the issuer URL
    // of the idp,
    // we still need to fetch the entity configuration directly afterward to get the full
    // entity statement

    return fetchTrustedEntityConfiguration(issuer, trustedFederationStatement.body().jwks());
  }

  private EntityStatementJWS fetchTrustedEntityConfiguration(@NonNull URI sub, JWKSet trustStore) {

    var trustedEntityConfiguration = apiClient.fetchEntityConfiguration(sub);
    if (!trustedEntityConfiguration.isValidAt(clock.instant())) {
      throw FederationExceptions.entityStatementTimeNotValid(sub.toString());
    }

    if (!trustedEntityConfiguration.verifySignature(trustStore)) {
      throw FederationExceptions.untrustedFederationStatement(sub.toString());
    }

    if (!trustStore.equals(trustedEntityConfiguration.body().jwks())
        && !trustedEntityConfiguration.verifySelfSigned()) {
      throw FederationExceptions.entityStatementBadSignature(sub.toString());
    }

    return trustedEntityConfiguration;
  }

  private EntityStatementJWS fetchTrustedFederationStatement(URI issuer) {
    var masterEntityConfiguration = apiClient.fetchEntityConfiguration(fedMasterUri);
    assertValidMasterEntityStatement(masterEntityConfiguration);

    var federationFetchEndpoint = getFederationFetchEndpoint(masterEntityConfiguration.body());

    return fetchTrustedFederationStatement(
        federationFetchEndpoint, masterEntityConfiguration.body().jwks(), issuer);
  }

  private EntityStatementJWS fetchTrustedFederationStatement(
      URI federationFetchEndpoint, JWKSet fedmasterTrustStore, URI issuer) {

    var federationStatement =
        apiClient.fetchFederationStatement(
            federationFetchEndpoint, fedMasterUri.toString(), issuer.toString());

    if (!federationStatement.isValidAt(clock.instant())) {
      throw FederationExceptions.federationStatementTimeNotValid(federationStatement.body().sub());
    }

    if (!federationStatement.verifySignature(fedmasterTrustStore)) {
      throw FederationExceptions.federationStatementBadSignature(issuer.toString());
    }

    return federationStatement;
  }

  private URI getFederationFetchEndpoint(EntityStatement entityStatement) {

    if (entityStatement == null) {
      throw FederationExceptions.entityStatementMissingFederationFetchUrl("");
    }

    if (entityStatement.metadata() == null
        || entityStatement.metadata().federationEntity() == null) {
      throw FederationExceptions.entityStatementMissingFederationFetchUrl(entityStatement.sub());
    }

    var uri = entityStatement.metadata().federationEntity().federationFetchEndpoint();

    if (uri == null || uri.isBlank()) {
      throw FederationExceptions.entityStatementMissingFederationFetchUrl(entityStatement.sub());
    }

    return URI.create(uri);
  }

  private void assertValidMasterEntityStatement(EntityStatementJWS entityStatement) {

    if (!entityStatement.isValidAt(clock.instant())) {
      throw FederationExceptions.entityStatementTimeNotValid(entityStatement.body().sub());
    }

    if (!entityStatement.verifySelfSigned()) {
      throw FederationExceptions.entityStatementBadSignature(entityStatement.body().sub());
    }
  }

  private List<IdpEntity> mustFetchIdpList() {

    var entityConfiguration = apiClient.fetchEntityConfiguration(fedMasterUri);

    var idpListEndpoint =
        entityConfiguration.body().metadata().federationEntity().idpListEndpoint();

    var idpJws = apiClient.fetchIdpList(URI.create(idpListEndpoint));
    if (idpJws == null || idpJws.body() == null || idpJws.body().idpEntities() == null) {
      throw FederationExceptions.emptyIdpList(fedMasterUri);
    }

    return idpJws.body().idpEntities();
  }
}
