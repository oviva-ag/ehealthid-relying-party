package com.oviva.gesundheitsid.fedclient;

import com.oviva.gesundheitsid.fedclient.api.EntityStatement;
import com.oviva.gesundheitsid.fedclient.api.EntityStatementJWS;
import com.oviva.gesundheitsid.fedclient.api.FederationApiClient;
import com.oviva.gesundheitsid.fedclient.api.IdpList.IdpEntity;
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

    if (!trustedFederationStatement.isValidAt(clock.instant())) {
      throw FederationExceptions.entityStatementTimeNotValid(
          trustedFederationStatement.body().sub());
    }

    // fetch the full statement from the idp and verify its signature
    var idpEntitytStatement = apiClient.fetchEntityConfiguration(issuer);
    if (!idpEntitytStatement.verifySignature(trustedFederationStatement.body().jwks())) {
      throw new RuntimeException("idp entity statement has untrusted signature");
    }

    if (!idpEntitytStatement.isValidAt(clock.instant())) {
      throw new RuntimeException("currently not valid, expired or not yet valid");
    }

    return idpEntitytStatement;
  }

  private EntityStatementJWS fetchTrustedFederationStatement(URI issuer) {
    var masterEntityConfiguration = apiClient.fetchEntityConfiguration(fedMasterUri);
    assertValidMasterEntityStatement(masterEntityConfiguration);

    var federationFetchEndpoint = getFederationFetchEndpoint(masterEntityConfiguration.body());

    var federationStatement =
        apiClient.fetchFederationStatement(
            federationFetchEndpoint, fedMasterUri.toString(), issuer.toString());

    if (!federationStatement.isValidAt(clock.instant())) {
      throw FederationExceptions.entityStatementTimeNotValid(federationStatement.body().sub());
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
      throw new RuntimeException("unable to fetch IDP list from '%s'".formatted(fedMasterUri));
    }

    return idpJws.body().idpEntities();
  }
}
