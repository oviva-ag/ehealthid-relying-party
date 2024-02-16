package com.oviva.ehealthid.relyingparty.ws;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider;
import com.oviva.ehealthid.auth.AuthenticationFlow;
import com.oviva.ehealthid.relyingparty.ConfigReader.Config;
import com.oviva.ehealthid.relyingparty.fed.FederationEndpoint;
import com.oviva.ehealthid.relyingparty.svc.ClientAuthenticator;
import com.oviva.ehealthid.relyingparty.svc.KeyStore;
import com.oviva.ehealthid.relyingparty.svc.SessionRepo;
import com.oviva.ehealthid.relyingparty.svc.TokenIssuer;
import com.oviva.ehealthid.util.JoseModule;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import jakarta.ws.rs.core.Application;
import java.util.Set;

public class App extends Application {

  private final Config config;
  private final SessionRepo sessionRepo;

  private final KeyStore keyStore;
  private final TokenIssuer tokenIssuer;

  private final AuthenticationFlow authenticationFlow;

  private final ClientAuthenticator clientAuthenticator;
  private final PrometheusMeterRegistry prometheusMeterRegistry;

  public App(
      Config config,
      SessionRepo sessionRepo,
      KeyStore keyStore,
      TokenIssuer tokenIssuer,
      AuthenticationFlow authenticationFlow,
      ClientAuthenticator clientAuthenticator,
      PrometheusMeterRegistry prometheusMeterRegistry) {
    this.config = config;
    this.sessionRepo = sessionRepo;
    this.keyStore = keyStore;
    this.tokenIssuer = tokenIssuer;
    this.authenticationFlow = authenticationFlow;
    this.clientAuthenticator = clientAuthenticator;
    this.prometheusMeterRegistry = prometheusMeterRegistry;
  }

  @Override
  public Set<Object> getSingletons() {

    return Set.of(
        new FederationEndpoint(config.federation()),
        new AuthEndpoint(
            config.baseUri(), config.relyingParty(), sessionRepo, tokenIssuer, authenticationFlow),
        new TokenEndpoint(tokenIssuer, clientAuthenticator),
        new OpenIdEndpoint(config.baseUri(), config.relyingParty(), keyStore),
        new JacksonJsonProvider(configureObjectMapper()),
        new HealthEndpoint(),
        new MetricsEndpoint(prometheusMeterRegistry));
  }

  @Override
  public Set<Class<?>> getClasses() {
    return Set.of(ThrowableExceptionMapper.class);
  }

  private ObjectMapper configureObjectMapper() {
    var om = new ObjectMapper();
    om.registerModule(new JoseModule());
    om.setSerializationInclusion(Include.NON_NULL);
    return om;
  }
}
