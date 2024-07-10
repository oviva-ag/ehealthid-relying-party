package com.oviva.ehealthid.relyingparty.util;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.oauth2.sdk.util.X509CertificateUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.net.URI;
import java.security.cert.CertificateEncodingException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.bouncycastle.operator.OperatorCreationException;

public class KeyGenerator {

  private KeyGenerator() {}

  @NonNull
  public static JWK generateSigningKeyWithCertificate(@NonNull URI issuer) {

    try {
      var key =
          new ECKeyGenerator(Curve.P_256)
              .keyUse(KeyUse.SIGNATURE)
              .keyIDFromThumbprint(true)
              .generate();

      var now = Instant.now();
      var nbf = now.minus(Duration.ofHours(3));

      // https://gemspec.gematik.de/docs/gemSpec/gemSpec_IDP_FD/gemSpec_IDP_FD_V1.7.2/#A_23185-01
      var exp = now.plus(Duration.ofDays(366)); // < 398d

      var cert =
          X509CertificateUtils.generateSelfSigned(
              new Issuer(issuer),
              Date.from(nbf),
              Date.from(exp),
              key.toPublicKey(),
              key.toPrivateKey());

      return new ECKey.Builder(key)
          .x509CertChain(List.of(Base64.encode(cert.getEncoded())))
          .build();
    } catch (IOException
        | OperatorCreationException
        | JOSEException
        | CertificateEncodingException e) {
      throw new IllegalStateException(
          "failed to generate signing key for issuer=%s".formatted(issuer), e);
    }
  }

  @NonNull
  public static JWK generateEncryptionKey() {
    try {
      return new ECKeyGenerator(Curve.P_256)
          .keyUse(KeyUse.ENCRYPTION)
          .keyIDFromThumbprint(true)
          .generate();
    } catch (JOSEException e) {
      throw new IllegalStateException("failed to generate encryption key", e);
    }
  }
}
