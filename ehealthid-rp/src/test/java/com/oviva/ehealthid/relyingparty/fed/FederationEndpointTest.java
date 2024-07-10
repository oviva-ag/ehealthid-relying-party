package com.oviva.ehealthid.relyingparty.fed;

import static com.oviva.ehealthid.relyingparty.test.EntityStatementJwsContentMatcher.jwsPayloadAt;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.oviva.ehealthid.fedclient.api.EntityStatementJWS;
import jakarta.ws.rs.SeBootstrap;
import jakarta.ws.rs.SeBootstrap.Configuration;
import jakarta.ws.rs.core.Application;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FederationEndpointTest {

  private static final URI ISSUER = URI.create("https://fachdienst.example.com");
  private static final URI FEDMASTER = URI.create("https://fedmaster.example.com");
  private static SeBootstrap.Instance server;

  @BeforeAll
  static void setUp() throws ExecutionException, InterruptedException, JOSEException {

    var signatureKey =
        new ECKeyGenerator(Curve.P_256)
            .keyIDFromThumbprint(true)
            .keyUse(KeyUse.SIGNATURE)
            .generate();

    var encryptionKey =
        new ECKeyGenerator(Curve.P_256)
            .keyIDFromThumbprint(true)
            .keyUse(KeyUse.ENCRYPTION)
            .generate();

    var config =
        FederationConfig.create()
            .iss(ISSUER)
            .sub(ISSUER)
            .redirectUris(List.of(ISSUER + "/callback"))
            .appName("My App")
            .scopes(List.of("openid", "email"))
            .federationMaster(FEDMASTER)
            .relyingPartyKeys(new JWKSet(encryptionKey))
            .entitySigningKeys(new JWKSet(signatureKey))
            .entitySigningKey(signatureKey)
            .ttl(Duration.ofMinutes(5))
            .build();

    server =
        SeBootstrap.start(
                new Application() {
                  @Override
                  public Set<Object> getSingletons() {
                    return Set.of(new FederationEndpoint(config));
                  }
                },
                Configuration.builder().host("127.0.0.1").port(0).build())
            .toCompletableFuture()
            .get();
  }

  @AfterAll
  static void tearDown() throws ExecutionException, InterruptedException, TimeoutException {
    server.stop().toCompletableFuture().get(3, TimeUnit.SECONDS);
  }

  @Test
  void get_basic() {
    given()
        .baseUri(server.configuration().baseUri().toString())
        .get("/.well-known/openid-federation")
        .then()
        .statusCode(200)
        .body(jwsPayloadAt("/iss", is(ISSUER.toString())))
        .body(jwsPayloadAt("/sub", is(ISSUER.toString())));
  }

  @Test
  void get() {

    var res =
        given()
            .baseUri(server.configuration().baseUri().toString())
            .get("/.well-known/openid-federation")
            .getBody();

    var body = res.asString();

    var es = EntityStatementJWS.parse(body);
    assertEquals(ISSUER.toString(), es.body().sub());
    assertEquals(ISSUER.toString(), es.body().iss());
    assertEquals(FEDMASTER.toString(), es.body().authorityHints().get(0));
  }
}
