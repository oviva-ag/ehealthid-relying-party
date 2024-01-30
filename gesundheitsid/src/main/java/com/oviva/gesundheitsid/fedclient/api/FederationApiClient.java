package com.oviva.gesundheitsid.fedclient.api;

import java.net.URI;

public interface FederationApiClient {

  EntityStatementJWS fetchFederationStatement(
      URI federationFetchUrl, String issuer, String subject);

  IdpListJWS fetchIdpList(URI idpListUrl);

  EntityStatementJWS fetchEntityConfiguration(URI entityUrl);
}
