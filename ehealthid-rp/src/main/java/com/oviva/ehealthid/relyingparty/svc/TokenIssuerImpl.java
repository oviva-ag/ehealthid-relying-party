package com.oviva.ehealthid.relyingparty.svc;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.oviva.ehealthid.auth.IdTokenJWS;
import com.oviva.ehealthid.relyingparty.svc.SessionRepo.Session;
import com.oviva.ehealthid.relyingparty.util.IdGenerator;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

public class TokenIssuerImpl implements TokenIssuer {

  private final URI issuer;

  private final KeyStore keyStore;

  private final Duration TTL = Duration.ofSeconds(60);
  private final Clock clock = Clock.systemUTC();

  private final CodeRepo codeRepo;

  public TokenIssuerImpl(URI issuer, KeyStore keyStore, CodeRepo codeRepo) {
    this.issuer = issuer;
    this.keyStore = keyStore;
    this.codeRepo = codeRepo;
  }

  @Override
  public Code issueCode(Session session, IdTokenJWS idTokenJWS) {
    var code = IdGenerator.generateID();
    var value =
        new Code(
            code,
            clock.instant(),
            clock.instant().plus(TTL),
            session.redirectUri(),
            session.nonce(),
            session.clientId(),
            idTokenJWS);
    codeRepo.save(value);
    return value;
  }

  @Override
  public Token redeem(@NonNull String code, String redirectUri, String clientId) {

    var redeemed = codeRepo.remove(code).orElse(null);
    if (redeemed == null) {
      return null;
    }

    if (!validateCode(redeemed, redirectUri, clientId)) {
      return null;
    }

    var accessTokenTtl = Duration.ofMinutes(5);
    return new Token(
        issueAccessToken(accessTokenTtl, redeemed.clientId()),
        issueIdToken(redeemed.clientId(), redeemed.nonce(), redeemed.federatedIdToken()),
        accessTokenTtl.getSeconds());
  }

  private boolean validateCode(Code code, String redirectUri, String clientId) {

    if (code.expiresAt().isBefore(clock.instant())) {
      return false;
    }

    if (redirectUri == null || clientId == null) {
      return false;
    }

    if (!code.redirectUri().toString().equals(redirectUri)) {
      return false;
    }

    return code.clientId().equals(clientId);
  }

  private String issueIdToken(String audience, String nonce, IdTokenJWS federatedIdToken) {
    try {
      var jwk = keyStore.signingKey();
      var signer = new ECDSASigner(jwk);

      // Prepare JWT with claims set
      var now = clock.instant();
      var claimsBuilder =
          new JWTClaimsSet.Builder()
              .issuer(issuer.toString())
              .audience(audience)
              .subject(federatedIdToken.body().sub()) // propagate original `sub`
              .issueTime(Date.from(now))
              .expirationTime(Date.from(now.plus(Duration.ofHours(8))));

      if (nonce != null) {
        claimsBuilder.claim("nonce", nonce);
      }

      // complete list of scopes and corresponding claims:
      // https://fachportal.gematik.de/fachportal-import/files/gemSpec_IDP_Sek_V2.0.1.pdf
      // Specification 4.2.4  - A_22989 -
      claimsBuilder.claim("urn:telematik:claims:id", federatedIdToken.body().telematikKvnr());

      // GesundheitsID specific claims according to Gematik reference IDP:
      //
      // "acr" : "gematik-ehealth-loa-high",
      // "amr" : "TODO amr",
      // "email" : null,
      // "urn:telematik:claims:profession" : "1.2.276.0.76.4.49",
      // "urn:telematik:claims:given_name" : null,
      // "urn:telematik:claims:id" : "X110411675",
      // "urn:telematik:claims:email" : "darius_michael@mail.boedefeld.de"

      var claims = claimsBuilder.build();

      var signedJWT =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(jwk.getKeyID()).build(), claims);

      signedJWT.sign(signer);

      return signedJWT.serialize();
    } catch (JOSEException e) {
      throw new RuntimeException(e);
    }
  }

  private String issueAccessToken(Duration ttl, String audience) {
    try {
      var jwk = keyStore.signingKey();
      var signer = new ECDSASigner(jwk);

      // Prepare JWT with claims set
      var now = clock.instant();
      var claims =
          new JWTClaimsSet.Builder()
              .issuer(issuer.toString())
              .audience(audience)
              .subject(UUID.randomUUID().toString())
              .issueTime(Date.from(now))
              .expirationTime(Date.from(now.plus(ttl)))
              .build();

      var signedJWT =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(jwk.getKeyID()).build(), claims);

      signedJWT.sign(signer);

      return signedJWT.serialize();
    } catch (JOSEException e) {
      throw new RuntimeException(e);
    }
  }
}
