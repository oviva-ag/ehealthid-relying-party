package com.oviva.ehealthid.fedclient;

import com.oviva.ehealthid.fedclient.api.EntityStatementJWS;
import java.net.URI;
import java.util.List;

public interface FederationMasterClient {

  List<IdpEntry> listAvailableIdps();

  EntityStatementJWS establishIdpTrust(URI issuer);
}
