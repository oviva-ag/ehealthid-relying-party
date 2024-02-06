package com.oviva.ehealthid.fedclient.api;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.net.URI;

public interface FederationApiClient {

  @NonNull
  EntityStatementJWS fetchFederationStatement(
      URI federationFetchUrl, String issuer, String subject);

  @NonNull
  IdpListJWS fetchIdpList(URI idpListUrl);

  @NonNull
  EntityStatementJWS fetchEntityConfiguration(URI entityUrl);
}
