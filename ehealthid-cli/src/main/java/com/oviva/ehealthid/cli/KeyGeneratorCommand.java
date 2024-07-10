package com.oviva.ehealthid.cli;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "keygen",
    mixinStandardHelpOptions = true,
    description =
        """
        Generator for JSON web keys (JWKS) for use in the TI OpenID federation.
        """)
public class KeyGeneratorCommand implements Callable<Integer> {

  @CommandLine.Option(
      names = {"-i", "--iss", "--issuer-uri"},
      description = "the issuer uri of the 'Fachdienst' identiy provider",
      required = true,
      defaultValue = "default")
  private URI issuerUri;

  private static final Logger logger = LoggerFactory.getLogger(KeyGeneratorCommand.class);

  public Integer call() throws Exception {

    var sigName = "sig";

    logger.atInfo().log("generating signing keys");
    var federationSigningKeys = generateSigningKey();

    saveJwks(sigName + "_" + deriveName(issuerUri), new JWKSet(List.of(federationSigningKeys)));

    return 0;
  }

  private String deriveName(URI issuer) {
    var s = issuer.toString();
    s = s.replaceAll("^https://", "");
    s = s.replaceAll("(/*)$", "");

    s = s.replaceAll("[^_A-Za-z0-9]+", "_");
    return s;
  }

  private JWK generateSigningKey() throws JOSEException {

    // https://gemspec.gematik.de/docs/gemSpec/gemSpec_IDP_FD/gemSpec_IDP_FD_V1.7.2/#A_23185-01
    var now = Instant.now();
    var nbf = now.minus(Duration.ofHours(24));
    var exp = now.plus(Duration.ofDays(366)); // < 398d

    return new ECKeyGenerator(Curve.P_256)
        .keyUse(KeyUse.SIGNATURE)
        .keyIDFromThumbprint(true)
        .notBeforeTime(Date.from(nbf))
        .expirationTime(Date.from(exp))
        .generate();
  }

  private void saveJwks(String name, JWKSet set) throws IOException {
    var jwks = set.toString(false);
    writeString(Path.of("%s_jwks.json".formatted(name)), jwks);
  }

  private void writeString(Path p, String contents) throws IOException {

    Files.writeString(p, contents, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
    logger.atInfo().log("written JWKS to '%s'".formatted(p));
  }
}
