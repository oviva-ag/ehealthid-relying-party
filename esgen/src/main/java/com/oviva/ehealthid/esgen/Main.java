package com.oviva.ehealthid.esgen;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyType;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.oviva.ehealthid.esgen.RegistratonFormRenderer.Model;
import com.oviva.ehealthid.esgen.RegistratonFormRenderer.Model.Environment;
import com.oviva.ehealthid.esgen.RegistratonFormRenderer.Model.Scope;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.ParseException;
import java.util.List;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "esgen",
    mixinStandardHelpOptions = true,
    version = "0.1",
    description =
        """
        Generator for entity-statements in the TI federation. This can generate the necessary keys as well as the registration xml.
        """)
public class Main implements Callable<Integer> {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  @Option(
      names = {"-e", "--environment"},
      description =
          "the environment to register for, either TU (Testumgebung), RU (Referenzumgebung) or PU (Produktivumgebung)",
      defaultValue = "TU",
      required = true)
  private Environment environment;

  @Option(
      names = {"-o", "--organisation-name"},
      description = "the organisation to register",
      required = true)
  private String organisationName;

  @Option(
      names = {"-m", "--member-id"},
      description = "the member ID to register for, needs to be requested from Gematik",
      required = true)
  private String memberId;

  @Option(
      names = {"-s", "--scopes"},
      description = "the scopes to register for",
      defaultValue = "INSURED_PERSON,EMAIL,DISPLAY_NAME",
      split = ",",
      required = true)
  private List<Scope> scopes;

  @Option(
      names = {"-i", "--iss", "--issuer-uri"},
      description = "the issuer uri of the 'Fachdienst' identiy provider",
      required = true)
  private URI issuerUri;

  @Option(
      names = {"--generate-keys"},
      description = "whether to generate new keys or not")
  private boolean generateKeys;

  @Option(
      names = {"--signing-jwks"},
      description =
          "the path to the signing keys in case an existing one should be re-used, in JWKS format")
  private Path signingKeyPath;

  @Option(
      names = {"--encryption-jwks"},
      description =
          "the path to the encryption keys in case an existing one should be re-used, in JWKS format")
  private Path encryptionKeyPath;

  public static void main(String[] args) {

    int exitCode = 1;
    try {
      var main = new Main();
      exitCode = new CommandLine(main).execute(args);
    } catch (Exception e) {
      logger.atError().setCause(e).log("key generator failed");
      System.exit(1);
    }
    System.exit(exitCode);
  }

  public Integer call() throws Exception { // your business logic goes here...

    var federationSigningKeys = getSigningKeys();

    var sigName = "sig";
    var encName = "enc";

    var federationIdTokenEncryptionKey = getEncryptionKeys();

    if (generateKeys) {
      saveJwks(sigName, federationSigningKeys);
    }
    if (generateKeys) {
      saveJwks(encName, federationIdTokenEncryptionKey);
    }

    writeRegistrationForm(federationSigningKeys);
    return 0;
  }

  private void writeRegistrationForm(JWKSet signingKeys) throws IOException {
    var registrationForm =
        RegistratonFormRenderer.render(
            new Model(
                memberId,
                organisationName,
                issuerUri,
                environment,
                scopes,
                signingKeys.toPublicJWKSet()));

    var path = Path.of("federation_registration_form.xml");
    logger.atInfo().log("writing registration form to '{}'", path);
    Files.writeString(path, registrationForm);
  }

  private JWKSet getSigningKeys() throws JOSEException {
    if (signingKeyPath != null && generateKeys) {
      logger
          .atError()
          .log("passed path for signing JWKS as well as flag to generate keys, pick one");
      throw new IllegalArgumentException("ambiguous singing key JWKS");
    }
    if (signingKeyPath != null) {
      logger.atInfo().log("loading signing keys from {}", signingKeyPath);
      return loadJwks(signingKeyPath, KeyUse.SIGNATURE);
    } else if (generateKeys) {
      logger.atInfo().log("generating signing keys");
      return generateJwks(KeyUse.SIGNATURE);
    }
    throw new IllegalArgumentException("no JWKS for signing keys provided or generated");
  }

  private JWKSet getEncryptionKeys() throws JOSEException {
    if (encryptionKeyPath != null && generateKeys) {
      logger
          .atError()
          .log("passed path for encryption JWKS as well as flag to generate keys, pick one");
      throw new IllegalArgumentException("ambiguous encryption key JWKS");
    }
    if (encryptionKeyPath != null) {
      logger.atInfo().log("loading encryption keys from {}", encryptionKeyPath);
      return loadJwks(encryptionKeyPath, KeyUse.ENCRYPTION);
    } else if (generateKeys) {
      logger.atInfo().log("generating encryption keys");
      return generateJwks(KeyUse.ENCRYPTION);
    }
    throw new IllegalArgumentException("no JWKS for encryption keys provided or generated");
  }

  private JWKSet generateJwks(KeyUse keyUse) throws JOSEException {
    var key = new ECKeyGenerator(Curve.P_256).keyUse(keyUse).keyIDFromThumbprint(true).generate();
    return new JWKSet(key);
  }

  private JWKSet loadJwks(Path path, KeyUse keyUse) {
    if (path == null) {
      throw new IllegalArgumentException("no path for JWKS given");
    }
    try {
      var contents = Files.readString(path);
      var jwks = JWKSet.parse(contents);
      for (var k : jwks.getKeys()) {
        validateKey(k, keyUse);
      }

      return jwks;
    } catch (IOException e) {
      throw new IllegalArgumentException(
          "failed to read JWKS at '%s'".formatted(path.toAbsolutePath()), e);
    } catch (ParseException e) {
      throw new IllegalArgumentException(
          "failed to parse JWKS at '%s'".formatted(path.toAbsolutePath()), e);
    }
  }

  private void validateKey(JWK key, KeyUse keyUse) {

    if (key.getKeyID() == null || key.getKeyID().isBlank()) {
      throw new IllegalArgumentException("JWK has a blank 'kid'");
    }

    if (key.getKeyUse() != keyUse) {
      throw new IllegalArgumentException(
          "JWK has unexpected use, expected '%s' but got '%s'".formatted(keyUse, key.getKeyUse()));
    }

    if (key.getKeyType() != KeyType.EC) {
      throw new IllegalArgumentException(
          "JWK has unexpected `typ`, expected %s but got %s"
              .formatted(KeyType.EC, key.getKeyType()));
    }

    var ecKey = key.toECKey();
    if (ecKey.getCurve() != Curve.P_256) {

      throw new IllegalArgumentException(
          "EC JWK has unexpected curve `crv`, expected %s but got %s"
              .formatted(Curve.P_256, ecKey.getCurve()));
    }
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
