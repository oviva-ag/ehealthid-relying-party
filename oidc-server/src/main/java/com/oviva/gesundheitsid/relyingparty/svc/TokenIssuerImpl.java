package com.oviva.gesundheitsid.relyingparty.svc;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.oviva.gesundheitsid.relyingparty.svc.SessionRepo.Session;
import com.oviva.gesundheitsid.relyingparty.util.IdGenerator;
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
  public Code issueCode(Session session) {
    var code = IdGenerator.generateID();
    var value =
        new Code(
            code,
            clock.instant(),
            clock.instant().plus(TTL),
            session.redirectUri(),
            session.nonce(),
            session.clientId());
    codeRepo.save(value);
    return value;
  }

  @Override
  public Token redeem(@NonNull String code) {
    var redeemed = codeRepo.remove(code).orElse(null);
    if (redeemed == null) {
      return null;
    }

    if (redeemed.expiresAt().isBefore(clock.instant())) {
      return null;
    }

    var accessTokenTtl = Duration.ofMinutes(5);
    return new Token(
        issueAccessToken(accessTokenTtl, redeemed.clientId()),
        issueIdToken(redeemed.clientId(), redeemed.nonce()),
        accessTokenTtl.getSeconds());
  }

  private String issueIdToken(String audience, String nonce) {
    try {
      var jwk = keyStore.signingKey();
      var signer = new ECDSASigner(jwk);

      // Prepare JWT with claims set
      var now = clock.instant();
      var claimsBuilder =
          new JWTClaimsSet.Builder()
              .issuer(issuer.toString())
              .audience(audience)
              .subject(UUID.randomUUID().toString())
              .issueTime(Date.from(now))
              .expirationTime(Date.from(now.plus(Duration.ofHours(8))));

      if (nonce != null) {
        claimsBuilder.claim("nonce", nonce);
      }

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
