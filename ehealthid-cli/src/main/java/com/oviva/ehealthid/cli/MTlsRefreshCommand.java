package com.oviva.ehealthid.cli;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.oauth2.sdk.util.X509CertificateUtils;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateEncodingException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import org.bouncycastle.operator.OperatorCreationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

@CommandLine.Command(
    name = "tls-refresh",
    mixinStandardHelpOptions = true,
    description = "Generate or Refresh the mTLS certificates in the Signing Key.")
public class MTlsRefreshCommand implements Callable<Integer> {

  @CommandLine.Option(
      names = {"-i", "--iss", "--issuer-uri"},
      description = "the issuer uri of the 'Fachdienst' identiy provider",
      required = false)
  private URI issuerUri;

  @CommandLine.Option(
      names = {"-e", "--existing"},
      description = "the existing signing key",
      required = true)
  private String existingKey;

  @CommandLine.Option(
      names = {"-r", "--refresh"},
      description = "refresh the existing mTLS certificate",
      defaultValue = "false")
  private boolean refresh;

  private static final Logger logger = LoggerFactory.getLogger(MTlsRefreshCommand.class);

  public Integer call() throws Exception {

    var sigName = "sig";

    logger.atInfo().log("using existing signing key '%s'".formatted(existingKey));
    var inputStream = Files.newInputStream(Path.of(existingKey));
    var jwks = JWKSet.load(inputStream);
    var key = jwks.getKeys().get(0);
    if (refresh) {
      var newKey = generateCertificate(key);
      System.out.println(newKey);
      logger.atInfo().log("refreshing mTLS certificate");
    } else {
      var newKey = generateCertificateFromExistingKey(key);
      logger.atInfo().log("generating mTLS certificate");
      System.out.println(newKey);
    }
    return 0;
  }

  private JWK generateCertificateFromExistingKey(JWK key)
      throws JOSEException, IOException, OperatorCreationException, CertificateEncodingException {
    // The idea here is to give users that currently have keys, that do not have
    // the relevant x5c field, the ability to generate a certificate for mTLS
    // without going through the fuzz of generating a new key and talking to
    // Gematik/BfArM about it. Which just generates work for everyone.

    var now = Instant.now();
    var nbf = now.minus(Duration.ofDays(1));
    var exp = now.plus(Duration.ofDays(180));

    var cert =
        X509CertificateUtils.generateSelfSigned(
            new Issuer(issuerUri),
            Date.from(nbf),
            Date.from(exp),
            key.toECKey().toPublicKey(),
            key.toECKey().toPrivateKey());

    return new ECKey.Builder(key.toECKey())
        .x509CertChain(List.of(Base64.encode(cert.getEncoded())))
        .build();
  }

  private JWK generateCertificate(JWK key)
      throws JOSEException, IOException, OperatorCreationException, CertificateEncodingException {
    // TODO: This is one huge playground method
    // Non of this is 'real' in the sense that it should work or is
    // what the standard would require.
    // My current understanding is that is that to keep in line with the
    // standard and the intended process, we should generate a new certificate
    // sign it with the existing key and then persist the new key and the new
    // certificate for use with mTLS.
    // Again, not what this currently does.
    var certKey =
        new ECKeyGenerator(Curve.P_256)
            .keyUse(KeyUse.SIGNATURE)
            .keyIDFromThumbprint(true)
            .generate();

    var now = Instant.now();
    var nbf = now.minus(Duration.ofDays(1));
    var exp = now.plus(Duration.ofDays(180));

    var cert =
        X509CertificateUtils.generateSelfSigned(
            new Issuer(issuerUri),
            Date.from(nbf),
            Date.from(exp),
            certKey.toPublicKey(),
            certKey.toPrivateKey());

    var chain = key.getX509CertChain();
    var newChain = new java.util.ArrayList<>(chain);
    newChain.add(Base64.encode(cert.getEncoded()));

    // TODO also persist the new signing key

    return new ECKey.Builder(key.toECKey()).x509CertChain(newChain).build();
  }
}
