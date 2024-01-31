package com.oviva.gesundheitsid.fedclient;

import com.nimbusds.jose.jwk.JWKSet;
import com.oviva.gesundheitsid.fedclient.api.EntityStatement;
import com.oviva.gesundheitsid.fedclient.api.EntityStatementJWS;
import com.oviva.gesundheitsid.fedclient.api.FederationApiClient;
import com.oviva.gesundheitsid.fedclient.api.IdpList.IdpEntity;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.net.URI;
import java.time.Clock;
import java.util.List;

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
