package com.oviva.ehealthid.relyingparty.ws;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider;
import com.nimbusds.jose.jwk.ECKey;
import com.oviva.ehealthid.relyingparty.ConfigReader.Config;
import com.oviva.ehealthid.relyingparty.fed.FederationEndpoint;
import com.oviva.ehealthid.relyingparty.providers.KeyStores;
import com.oviva.ehealthid.relyingparty.svc.AuthService;
import com.oviva.ehealthid.relyingparty.svc.ClientAuthenticator;
import com.oviva.ehealthid.relyingparty.svc.TokenIssuer;
import com.oviva.ehealthid.util.JoseModule;
import jakarta.ws.rs.core.Application;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jboss.resteasy.plugins.providers.ByteArrayProvider;
import org.jboss.resteasy.plugins.providers.StringTextStar;

public class App extends Application {

  private final Config config;
  private final Supplier<ECKey> openIdProviderSigningKeys;
  private final KeyStores keyStores;
  private final TokenIssuer tokenIssuer;
  private final ClientAuthenticator clientAuthenticator;

  private final AuthService authService;

  public App(
      Config config,
      KeyStores keyStores,
      TokenIssuer tokenIssuer,
      ClientAuthenticator clientAuthenticator,
      AuthService authService) {
    this.config = config;
    this.keyStores = keyStores;
    this.tokenIssuer = tokenIssuer;
    this.clientAuthenticator = clientAuthenticator;
    this.authService = authService;

    this.openIdProviderSigningKeys =
        () -> {
          var ks = keyStores.openIdProviderJwksKeystore();
          return ks.keys().get(0);
        };
  }

  @Override
  public Set<Object> getSingletons() {

    var singletons =
        Set.of(
            new FederationEndpoint(
                config.federation(),
                new FederationKeysAdapter(config.federation().sub(), keyStores)),
            new AuthEndpoint(authService, config.appUri()),
            new TokenEndpoint(tokenIssuer, clientAuthenticator),
            new OpenIdEndpoint(config.baseUri(), config.relyingParty(), openIdProviderSigningKeys),
            new ThrowableExceptionMapper(config.appUri()),
            new JacksonJsonProvider(configureObjectMapper()));

    if (RequestLogDumpProvider.isEnabled()) {
      singletons =
          Stream.concat(singletons.stream(), Stream.of(new RequestLogDumpProvider()))
              .collect(Collectors.toSet());
    }
    return singletons;
  }

  @Override
  public Set<Class<?>> getClasses() {

    // https://github.com/resteasy/resteasy/blob/f5fedb83d75ac88cad8fe79c0711b46a9db6a5ed/resteasy-core/src/main/resources/META-INF/services/jakarta.ws.rs.ext.Providers
    return Set.of(StringTextStar.class, ByteArrayProvider.class);
  }

  private ObjectMapper configureObjectMapper() {
    var om = new ObjectMapper();
    om.registerModule(new JoseModule());
    om.setSerializationInclusion(Include.NON_NULL);
    return om;
  }
}
