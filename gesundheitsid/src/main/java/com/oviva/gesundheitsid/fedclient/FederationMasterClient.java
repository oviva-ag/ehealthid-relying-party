package com.oviva.gesundheitsid.fedclient;

import com.oviva.gesundheitsid.fedclient.api.EntityStatementJWS;
import java.net.URI;
import java.util.List;

public interface FederationMasterClient {

  List<IdpEntry> listAvailableIdps();

  EntityStatementJWS establishIdpTrust(URI issuer);
}
