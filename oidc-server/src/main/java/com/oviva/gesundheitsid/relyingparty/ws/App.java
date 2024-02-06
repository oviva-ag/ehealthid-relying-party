package com.oviva.gesundheitsid.relyingparty.ws;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider;
import com.oviva.gesundheitsid.auth.AuthenticationFlow;
import com.oviva.gesundheitsid.relyingparty.cfg.RelyingPartyConfig;
import com.oviva.gesundheitsid.relyingparty.fed.FederationConfig;
import com.oviva.gesundheitsid.relyingparty.fed.FederationEndpoint;
import com.oviva.gesundheitsid.relyingparty.svc.KeyStore;
import com.oviva.gesundheitsid.relyingparty.svc.SessionRepo;
import com.oviva.gesundheitsid.relyingparty.svc.TokenIssuer;
import com.oviva.gesundheitsid.util.JoseModule;
import jakarta.ws.rs.core.Application;
import java.util.Set;

public class App extends Application {

  private final RelyingPartyConfig relyingPartyConfig;
  private final FederationConfig federationConfig;
  private final SessionRepo sessionRepo;

  private final KeyStore keyStore;
  private final TokenIssuer tokenIssuer;

  private final AuthenticationFlow authenticationFlow;

  public App(
      RelyingPartyConfig relyingPartyConfig,
      FederationConfig federationConfig,
      SessionRepo sessionRepo,
      KeyStore keyStore,
      TokenIssuer tokenIssuer,
      AuthenticationFlow authenticationFlow) {
    this.relyingPartyConfig = relyingPartyConfig;
    this.federationConfig = federationConfig;
    this.sessionRepo = sessionRepo;
    this.keyStore = keyStore;
    this.tokenIssuer = tokenIssuer;
    this.authenticationFlow = authenticationFlow;
  }

  @Override
  public Set<Object> getSingletons() {

    return Set.of(
        new FederationEndpoint(federationConfig),
        new AuthEndpoint(relyingPartyConfig, sessionRepo, tokenIssuer, authenticationFlow),
        new OpenIdEndpoint(relyingPartyConfig, keyStore),
        new JacksonJsonProvider(configureObjectMapper()));
  }

  private ObjectMapper configureObjectMapper() {
    var om = new ObjectMapper();
    om.registerModule(new JoseModule());
    om.setSerializationInclusion(Include.NON_NULL);
    return om;
  }
}
