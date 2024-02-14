package com.oviva.ehealthid.relyingparty.svc;

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.oviva.ehealthid.relyingparty.svc.ClientAuthenticator.Request;
import java.net.URI;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClientAuthenticatorTest {

  private final URI RP_ISSUER = URI.create("https://rp.example.com");
  private final String CLIENT_ID = "myRpBroker";

  @Test
  void authenticate() throws JOSEException {

    var key = generateKey();
    var jwkSource = new StaticJwkSource<>(key);

    var claims =
        new JWTClaimsSet.Builder()
            .audience(RP_ISSUER.toString())
            .subject(CLIENT_ID)
            .issuer(CLIENT_ID)
            .expirationTime(Date.from(Instant.now().plusSeconds(60)))
            .jwtID(UUID.randomUUID().toString())
            .build();

    var signed = signJwt(claims, key);

    var authenticator = new ClientAuthenticator(jwkSource, RP_ISSUER);

    // when & then
    assertDoesNotThrow(
        () ->
            authenticator.authenticate(
                new Request(
                    CLIENT_ID, ClientAuthenticator.CLIENT_ASSERTION_TYPE_PRIVATE_KEY_JWT, signed)));
  }

  @Test
  void authenticate_badIssuer() throws JOSEException {

    var key = generateKey();
    var jwkSource = new StaticJwkSource<>(key);

    var claims =
        new JWTClaimsSet.Builder()
            .audience(RP_ISSUER.toString())
            .subject(CLIENT_ID)
            .issuer("fake_issuer")
            .expirationTime(Date.from(Instant.now().plusSeconds(60)))
            .jwtID(UUID.randomUUID().toString())
            .build();

    var signed = signJwt(claims, key);

    var authenticator = new ClientAuthenticator(jwkSource, RP_ISSUER);

    // when & then
    assertThrows(
        AuthenticationException.class,
        () ->
            authenticator.authenticate(
                new Request(
                    CLIENT_ID, ClientAuthenticator.CLIENT_ASSERTION_TYPE_PRIVATE_KEY_JWT, signed)));
  }

  @Test
  void authenticate_badSubject() throws JOSEException {

    var key = generateKey();
    var jwkSource = new StaticJwkSource<>(key);

    var claims =
        new JWTClaimsSet.Builder()
            .audience(RP_ISSUER.toString())
            .subject("not the right client")
            .issuer(CLIENT_ID)
            .expirationTime(Date.from(Instant.now().plusSeconds(60)))
            .jwtID(UUID.randomUUID().toString())
            .build();

    var signed = signJwt(claims, key);

    var authenticator = new ClientAuthenticator(jwkSource, RP_ISSUER);

    // when & then
    assertThrows(
        AuthenticationException.class,
        () ->
            authenticator.authenticate(
                new Request(
                    CLIENT_ID, ClientAuthenticator.CLIENT_ASSERTION_TYPE_PRIVATE_KEY_JWT, signed)));
  }

  @Test
  void authenticate_missingJwtId() throws JOSEException {

    var key = generateKey();
    var jwkSource = new StaticJwkSource<>(key);

    var claims =
        new JWTClaimsSet.Builder()
            .audience(RP_ISSUER.toString())
            .subject("not the right client")
            .issuer(CLIENT_ID)
            .expirationTime(Date.from(Instant.now().plusSeconds(60)))
            .build();

    var signed = signJwt(claims, key);

    var authenticator = new ClientAuthenticator(jwkSource, RP_ISSUER);

    // when & then
    assertThrows(
        AuthenticationException.class,
        () ->
            authenticator.authenticate(
                new Request(
                    CLIENT_ID, ClientAuthenticator.CLIENT_ASSERTION_TYPE_PRIVATE_KEY_JWT, signed)));
  }

  @Test
  void authenticate_expired() throws JOSEException {

    var key = generateKey();
    var jwkSource = new StaticJwkSource<>(key);

    var inThePast = Date.from(Instant.now().minusSeconds(60));

    var claims =
        new JWTClaimsSet.Builder()
            .audience(RP_ISSUER.toString())
            .subject("not the right client")
            .issuer(CLIENT_ID)
            .expirationTime(inThePast)
            .build();

    var signed = signJwt(claims, key);

    var authenticator = new ClientAuthenticator(jwkSource, RP_ISSUER);

    // when & then
    assertThrows(
        AuthenticationException.class,
        () ->
            authenticator.authenticate(
                new Request(
                    CLIENT_ID, ClientAuthenticator.CLIENT_ASSERTION_TYPE_PRIVATE_KEY_JWT, signed)));
  }

  @Test
  void authenticate_missingKey() throws JOSEException {

    var actualSigningKey = generateKey();
    var anotherKey = generateKey();

    var jwkSource = new StaticJwkSource<>(anotherKey);

    var inThePast = Date.from(Instant.now().minusSeconds(60));

    var claims =
        new JWTClaimsSet.Builder()
            .audience(RP_ISSUER.toString())
            .subject("not the right client")
            .issuer(CLIENT_ID)
            .expirationTime(inThePast)
            .build();

    var signed = signJwt(claims, actualSigningKey);

    var authenticator = new ClientAuthenticator(jwkSource, RP_ISSUER);

    // when & then
    assertThrows(
        AuthenticationException.class,
        () ->
            authenticator.authenticate(
                new Request(
                    CLIENT_ID, ClientAuthenticator.CLIENT_ASSERTION_TYPE_PRIVATE_KEY_JWT, signed)));
  }

  @Test
  void authenticate_badAudience() throws JOSEException {

    var key = generateKey();
    var jwkSource = new StaticJwkSource<>(key);

    var claims =
        new JWTClaimsSet.Builder()
            .audience("https://bad-audience.example.com")
            .subject("not the right client")
            .issuer(CLIENT_ID)
            .expirationTime(Date.from(Instant.now().plusSeconds(60)))
            .build();

    var signed = signJwt(claims, key);

    var authenticator = new ClientAuthenticator(jwkSource, RP_ISSUER);

    // when & then
    assertThrows(
        AuthenticationException.class,
        () ->
            authenticator.authenticate(
                new Request(
                    CLIENT_ID, ClientAuthenticator.CLIENT_ASSERTION_TYPE_PRIVATE_KEY_JWT, signed)));
  }

  @Test
  void authenticate_badType() throws JOSEException {

    var authenticator = new ClientAuthenticator(null, RP_ISSUER);

    // when & then
    assertThrows(
        AuthenticationException.class,
        () -> authenticator.authenticate(new Request(null, "some_type", null)));
  }

  private String signJwt(JWTClaimsSet claims, ECKey key) throws JOSEException {

    var signer = new ECDSASigner(key);

    var header = new JWSHeader(JWSAlgorithm.ES256);
    var jws = new JWSObject(header, claims.toPayload());
    jws.sign(signer);

    return jws.serialize();
  }

  private ECKey generateKey() throws JOSEException {

    return new ECKeyGenerator(Curve.P_256)
        .keyIDFromThumbprint(true)
        .keyUse(KeyUse.SIGNATURE)
        .generate();
  }

  private record StaticJwkSource<T extends SecurityContext>(JWK key) implements JWKSource<T> {

    @Override
    public List<JWK> get(JWKSelector jwkSelector, T context) throws KeySourceException {
      return jwkSelector.select(new JWKSet(key));
    }
  }
}
