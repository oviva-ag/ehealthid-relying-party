package com.oviva.ehealthid.relyingparty.fed;

import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.oviva.ehealthid.fedclient.api.EntityStatement;
import com.oviva.ehealthid.fedclient.api.EntityStatement.FederationEntity;
import com.oviva.ehealthid.fedclient.api.EntityStatement.Metadata;
import com.oviva.ehealthid.fedclient.api.EntityStatement.OpenIdRelyingParty;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.List;

@Path("/")
public class FederationEndpoint {

  static final String MEDIA_TYPE_ENTITY_STATEMENT = "application/entity-statement+jwt";
  private final FederationConfig federationConfig;
  private final FederationKeys federationKeys;

  public FederationEndpoint(FederationConfig federationConfig, FederationKeys federationKeys) {
    this.federationConfig = federationConfig;
    this.federationKeys = federationKeys;
  }

  @Path("/.well-known/openid-federation")
  @GET
  @Produces(MEDIA_TYPE_ENTITY_STATEMENT)
  public Response get() {

    var federationEntityJwks = federationKeys.federationKeys();
    var relyingPartyJwks = federationKeys.relyingPartyJwks();

    var now = Instant.now();
    var exp = now.plus(federationConfig.ttl());

    var jws =
        EntityStatement.create()
            .iat(now)
            .nbf(now)
            .exp(exp)
            .iss(federationConfig.iss().toString())
            .sub(federationConfig.sub().toString())
            .authorityHints(List.of(federationConfig.federationMaster().toString()))
            .metadata(
                Metadata.create()
                    .openIdRelyingParty(
                        OpenIdRelyingParty.create()
                            .clientName(federationConfig.appName())
                            .jwks(relyingPartyJwks)
                            .responseTypes(List.of("code"))
                            .grantTypes(List.of("authorization_code"))
                            .requirePushedAuthorizationRequests(true)
                            .idTokenSignedResponseAlg("ES256")
                            .idTokenEncryptedResponseAlg("ECDH-ES")
                            .idTokenEncryptedResponseEnc("A256GCM")
                            .scope(String.join(" ", federationConfig.scopes()))
                            .redirectUris(federationConfig.redirectUris())
                            .clientRegistrationTypes(List.of("automatic"))
                            .tokenEndpointAuthMethodsSupported(
                                List.of("self_signed_tls_client_auth"))

                            // according to the federation spec this is not required here, some
                            // sectoral IdPs require it though
                            .defaultAcrValues(List.of("gematik-ehealth-loa-high"))

                            // warn: this is a non-standard field, but needed by some sectoral IdPs
                            .tokenEndpointAuthMethod("self_signed_tls_client_auth")
                            .build())
                    .federationEntity(
                        FederationEntity.create().name(federationConfig.appName()).build())
                    .build())
            .jwks(federationEntityJwks)
            .build()
            .sign(federationKeys.federationSigningKey());

    return Response.ok(jws.serialize())
        .header("x-kc-provider", "ovi")
        .cacheControl(cacheForTtl(now))
        .build();
  }

  public interface FederationKeys {

    /** key used to self-sign the entity key, MUST be registered in the federation */
    JWKSet federationKeys();

    /** key used to self-sign the entity key, MUST be registered in the federation */
    ECKey federationSigningKey();

    JWKSet relyingPartyJwks();
  }

  private CacheControl cacheForTtl(Instant now) {

    var cacheUntil = now.plusSeconds((long) (federationConfig.ttl().getSeconds() * 0.8));

    var cc = new CacheControl();
    cc.setMaxAge((int) cacheUntil.getEpochSecond());
    return cc;
  }
}
