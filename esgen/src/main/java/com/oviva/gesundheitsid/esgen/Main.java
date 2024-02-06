package com.oviva.gesundheitsid.esgen;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.Key;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) {

    var main = new Main();
    try {
      main.run();
    } catch (Exception e) {
      logger.atError().setCause(e).log("key generator failed");
      System.exit(1);
    }
  }

  public void run() throws JOSEException, IOException { // your business logic goes here...

    logger.atInfo().log("generating ES256 keys");

    var federationSigningKey =
        new ECKeyGenerator(Curve.P_256)
            .keyUse(KeyUse.SIGNATURE)
            .keyIDFromThumbprint(true)
            .generate();

    var sigName = "sig";
    var encName = "enc";

    saveJwks(sigName, new JWKSet(federationSigningKey));
    savePublicEncoded(sigName, federationSigningKey);

    var federationIdTokenEncryptionKey =
        new ECKeyGenerator(Curve.P_256)
            .keyUse(KeyUse.ENCRYPTION)
            .keyIDFromThumbprint(true)
            .generate();

    saveJwks(encName, new JWKSet(federationIdTokenEncryptionKey));
    savePublicEncoded(encName, federationIdTokenEncryptionKey);
  }

  private void saveJwks(String name, JWKSet set) throws IOException {
    var jwks = set.toString(false);
    writeString(Path.of("%s_jwks.json".formatted(name)), jwks);
  }

  private void savePublicEncoded(String name, ECKey jwk) throws JOSEException, IOException {

    var publicEncodedPem = encodeAsPem(jwk.toPublicKey(), "PUBLIC KEY");
    var p = Path.of("%s_pub.pem".formatted(name));
    writeString(p, publicEncodedPem);
  }

  private String encodeAsPem(Key k, String type) {

    var encoded = Base64.getEncoder().encodeToString(k.getEncoded());

    var sb = new StringBuilder();
    sb.append("-----BEGIN %s-----%n".formatted(type));

    while (!encoded.isEmpty()) {
      var end = Math.min(64, encoded.length());
      var line = encoded.substring(0, end);
      sb.append(line).append('\n');
      encoded = encoded.substring(end);
    }

    sb.append("-----END %s-----%n".formatted(type));
    return sb.toString();
  }

  private void writeString(Path p, String contents) throws IOException {

    Files.writeString(p, contents, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
    logger.atInfo().log("written '%s'".formatted(p));
  }
}
