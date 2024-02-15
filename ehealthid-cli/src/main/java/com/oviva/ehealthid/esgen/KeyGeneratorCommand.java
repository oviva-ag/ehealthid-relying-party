package com.oviva.ehealthid.esgen;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;

@Command(
    name = "keygen",
    mixinStandardHelpOptions = true,
    description =
        """
        Generator for JSON web keys (JWKS) for use in the TI OpenID federation.
        """)
public class KeyGeneratorCommand implements Callable<Integer> {

  private static final Logger logger = LoggerFactory.getLogger(KeyGeneratorCommand.class);

  public Integer call() throws Exception {

    var sigName = "sig";
    var encName = "enc";

    logger.atInfo().log("generating signing keys");
    var federationSigningKeys = generateJwks(KeyUse.SIGNATURE);

    logger.atInfo().log("generating encryption keys");
    var federationIdTokenEncryptionKey = generateJwks(KeyUse.ENCRYPTION);

    saveJwks(sigName, federationSigningKeys);
    saveJwks(encName, federationIdTokenEncryptionKey);

    return 0;
  }

  private JWKSet generateJwks(KeyUse keyUse) throws JOSEException {
    var key = new ECKeyGenerator(Curve.P_256).keyUse(keyUse).keyIDFromThumbprint(true).generate();
    return new JWKSet(key);
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
