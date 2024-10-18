package com.oviva.ehealthid.test;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.oauth2.sdk.util.X509CertificateUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.net.URI;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Random;
import org.bouncycastle.operator.OperatorCreationException;

public class ECKeyGenerator {

  private static final ECKey EXAMPLE = generateExample();

  private ECKeyGenerator() {}

  public static ECKey generateSigningKeyWithCertificate(@NonNull URI issuer) {

    try {
      var key = generateP256(null);

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
      throw new RuntimeException("failed to generate key", e);
    }
  }

  public static ECKey generate() {
    return generateP256(null);
  }

  public static ECKey example() {
    return EXAMPLE;
  }

  private static ECKey generateExample() {
    var notVerySecureRandom = new NotSoSecureRandom();
    return generateP256(notVerySecureRandom);
  }

  private static ECKey generateP256(SecureRandom sr) {
    try {
      return new com.nimbusds.jose.jwk.gen.ECKeyGenerator(Curve.P_256)
          .secureRandom(sr)
          .keyIDFromThumbprint(true)
          .generate();

    } catch (JOSEException e) {
      throw new RuntimeException("failed to generate key", e);
    }
  }

  private static class NotSoSecureRandom extends SecureRandom {

    private final Random prng = new Random(1337L);

    @Override
    public void nextBytes(byte[] bytes) {
      prng.nextBytes(bytes);
    }
  }
}
