package com.oviva.gesundheitsid.relyingparty.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider;
import com.oviva.gesundheitsid.relyingparty.cfg.Config;
import com.oviva.gesundheitsid.relyingparty.svc.KeyStore;
import com.oviva.gesundheitsid.relyingparty.svc.SessionRepo;
import com.oviva.gesundheitsid.relyingparty.svc.TokenIssuer;
import com.oviva.gesundheitsid.util.JoseModule;
import jakarta.ws.rs.core.Application;
import java.util.Set;

public class App extends Application {

  private final Config config;
  private final SessionRepo sessionRepo;

  private final KeyStore keyStore;
  private final TokenIssuer tokenIssuer;

  public App(Config config, SessionRepo sessionRepo, KeyStore keyStore, TokenIssuer tokenIssuer) {
    this.config = config;
    this.sessionRepo = sessionRepo;
    this.keyStore = keyStore;
    this.tokenIssuer = tokenIssuer;
  }

  @Override
  public Set<Object> getSingletons() {

    return Set.of(
        new OpenIdEndpoint(config, sessionRepo, tokenIssuer, keyStore),
        new JacksonJsonProvider(configureObjectMapper()));
  }

  private ObjectMapper configureObjectMapper() {
    var om = new ObjectMapper();
    om.registerModule(new JoseModule());
    return om;
  }
}
