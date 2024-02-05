package com.oviva.gesundheitsid.relyingparty.ws;

import com.nimbusds.jose.jwk.JWKSet;
import com.oviva.gesundheitsid.relyingparty.cfg.RelyingPartyConfig;
import com.oviva.gesundheitsid.relyingparty.svc.KeyStore;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Duration;
import java.util.List;

@Path("/")
public class OpenIdEndpoint {

  private final RelyingPartyConfig relyingPartyConfig;
  private final KeyStore keyStore;

  public OpenIdEndpoint(RelyingPartyConfig relyingPartyConfig, KeyStore keyStore) {
    this.relyingPartyConfig = relyingPartyConfig;
    this.keyStore = keyStore;
  }

  @GET
  @Path("/.well-known/openid-configuration")
  @Produces(MediaType.APPLICATION_JSON)
  public Response openIdConfiguration() {

    var body =
        new OpenIdConfiguration(
            relyingPartyConfig.baseUri().toString(),
            relyingPartyConfig.baseUri().resolve("/auth").toString(),
            relyingPartyConfig.baseUri().resolve("/token").toString(),
            relyingPartyConfig.baseUri().resolve("/jwks.json").toString(),
            List.of("openid"),
            relyingPartyConfig.supportedResponseTypes(),
            List.of("authorization_code"),
            List.of("public"),
            List.of("ES256"),
            List.of(),
            List.of());

    return Response.ok(body).build();
  }

  @GET
  @Path("/jwks.json")
  @Produces(MediaType.APPLICATION_JSON)
  public Response jwks() {
    var key = keyStore.signingKey().toPublicJWK();

    var cacheControl = new CacheControl();
    cacheControl.setMaxAge((int) Duration.ofMinutes(30).getSeconds());

    return Response.ok(new JWKSet(List.of(key))).cacheControl(cacheControl).build();
  }
}
