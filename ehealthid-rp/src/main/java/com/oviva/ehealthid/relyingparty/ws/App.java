package com.oviva.ehealthid.relyingparty.ws;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider;
import com.oviva.ehealthid.relyingparty.ConfigReader.Config;
import com.oviva.ehealthid.relyingparty.fed.FederationEndpoint;
import com.oviva.ehealthid.relyingparty.svc.AuthService;
import com.oviva.ehealthid.relyingparty.svc.ClientAuthenticator;
import com.oviva.ehealthid.relyingparty.svc.KeyStore;
import com.oviva.ehealthid.relyingparty.svc.TokenIssuer;
import com.oviva.ehealthid.util.JoseModule;
import jakarta.ws.rs.core.Application;
import java.util.Set;
import org.jboss.resteasy.plugins.providers.ByteArrayProvider;
import org.jboss.resteasy.plugins.providers.StringTextStar;

public class App extends Application {

  private final Config config;
  private final KeyStore keyStore;
  private final TokenIssuer tokenIssuer;
  private final ClientAuthenticator clientAuthenticator;

  private final AuthService authService;

  public App(
      Config config,
      KeyStore keyStore,
      TokenIssuer tokenIssuer,
      ClientAuthenticator clientAuthenticator,
      AuthService authService) {
    this.config = config;
    this.keyStore = keyStore;
    this.tokenIssuer = tokenIssuer;
    this.clientAuthenticator = clientAuthenticator;
    this.authService = authService;
  }

  @Override
  public Set<Object> getSingletons() {

    return Set.of(
        new FederationEndpoint(config.federation()),
        new AuthEndpoint(authService),
        new TokenEndpoint(tokenIssuer, clientAuthenticator),
        new OpenIdEndpoint(config.baseUri(), config.relyingParty(), keyStore),
        new JacksonJsonProvider(configureObjectMapper()));
  }

  @Override
  public Set<Class<?>> getClasses() {

    // https://github.com/resteasy/resteasy/blob/f5fedb83d75ac88cad8fe79c0711b46a9db6a5ed/resteasy-core/src/main/resources/META-INF/services/jakarta.ws.rs.ext.Providers
    return Set.of(ThrowableExceptionMapper.class, StringTextStar.class, ByteArrayProvider.class);
  }

  private ObjectMapper configureObjectMapper() {
    var om = new ObjectMapper();
    om.registerModule(new JoseModule());
    om.setSerializationInclusion(Include.NON_NULL);
    return om;
  }
}
