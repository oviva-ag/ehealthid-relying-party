package com.oviva.gesundheitsid.auth;

import com.oviva.gesundheitsid.auth.steps.SelectSectoralIdpStep;
import com.oviva.gesundheitsid.crypto.KeySupplier;
import com.oviva.gesundheitsid.fedclient.FederationMasterClient;
import com.oviva.gesundheitsid.fedclient.api.OpenIdClient;
import java.net.URI;
import java.util.List;

public class AuthenticationFlow {

  private final URI selfIssuer;
  private final FederationMasterClient federationMasterClient;

  private final OpenIdClient openIdClient;

  private final KeySupplier relyingPartyKeySupplier;

  public AuthenticationFlow(
      URI selfIssuer,
      FederationMasterClient federationMasterClient,
      OpenIdClient openIdClient,
      KeySupplier relyingPartyKeySupplier) {
    this.selfIssuer = selfIssuer;
    this.federationMasterClient = federationMasterClient;
    this.openIdClient = openIdClient;
    this.relyingPartyKeySupplier = relyingPartyKeySupplier;
  }

  public SelectSectoralIdpStep start(Session session) {

    return new SelectSectoralIdpStep(
        selfIssuer,
        federationMasterClient,
        openIdClient,
        relyingPartyKeySupplier,
        session.callbackUri(),
        session.nonce(),
        session.codeChallengeS256(),
        session.state(),
        session.scopes());
  }

  public record Session(
      String state, String nonce, URI callbackUri, String codeChallengeS256, List<String> scopes) {}
}
