package com.oviva.ehealthid.cli;

import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyType;
import com.nimbusds.jose.jwk.KeyUse;
import com.oviva.ehealthid.cli.forms.RegistratonFormRenderer;
import com.oviva.ehealthid.cli.forms.RegistratonFormRenderer.Model;
import com.oviva.ehealthid.cli.forms.RegistratonFormRenderer.Model.Environment;
import com.oviva.ehealthid.cli.forms.RegistratonFormRenderer.Model.Scope;
import com.oviva.ehealthid.fedclient.api.EntityStatementJWS;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "fedreg",
    mixinStandardHelpOptions = true,
    description =
        """
        Generate requests for registration in the eHealthID federation. Sent to Gematik.
        """)
public class FedRegistrationCommand implements Callable<Integer> {

  private static final Logger logger = LoggerFactory.getLogger(FedRegistrationCommand.class);

  @Option(
      names = {"-e", "--environment"},
      description =
          "the environment to register for, either TU (Testumgebung) or RU (Referenzumgebung)",
      defaultValue = "TU",
      required = true)
  private Environment environment;

  @Option(
      names = {"-m", "--member-id"},
      description = "the member ID to register for, needs to be requested from Gematik",
      required = true)
  private String memberId;

  @Option(
      names = {"-i", "--iss", "--issuer-uri"},
      description = "the issuer uri of the 'Fachdienst' identiy provider",
      required = true)
  private URI issuerUri;

  @Option(
      names = {"-f", "--file"},
      description = "the file to write to")
  private Path file;

  @Option(
      names = {"-c", "--contact-email"},
      description = "the technical contact email for the IdP")
  private String contactEmail;

  @Option(
      names = {"--vfs-confirmation"},
      description = "gematik-Verfahrensschlüssel (starts with VFS_DiGA_...)")
  private String vfsConfirmation;

  @Option(
      names = {"--proxy"},
      description =
          "if issuer-uri is behind a proxy, specify the proxy address (e.g. '172.0.0.0:4711')")
  private String proxy;

  @Option(
      names = {"--header"},
      description =
          "additional http headers (e.g. 'X-Authorization: Bearertoken'), can be repeated")
  private String[] httpHeaders;

  public static void main(String[] args) {

    int exitCode = 1;
    try {
      var main = new FedRegistrationCommand();
      exitCode = new CommandLine(main).execute(args);
    } catch (Exception e) {
      logger.atError().setCause(e).log("key generator failed");
      System.exit(1);
    }
    System.exit(exitCode);
  }

  public Integer call() throws Exception {

    parameterValidation();

    var entityConfiguration = fetchEntityConfiguration();
    for (var key : entityConfiguration.jwks().getKeys()) {
      validateKey(key, KeyUse.SIGNATURE);
    }

    if (file != null) {
      writeRegistrationForm(entityConfiguration);
    } else {
      printRegistrationForm(entityConfiguration);
    }

    return 0;
  }

  private void printRegistrationForm(EntityConfiguration entityConfiguration) {
    var registrationForm = renderRegistrationForm(entityConfiguration);
    System.out.println(registrationForm);
  }

  private void writeRegistrationForm(EntityConfiguration entityConfiguration) throws IOException {
    var registrationForm = renderRegistrationForm(entityConfiguration);

    logger.atInfo().log("writing registration form to '{}'", file);
    Files.writeString(file, registrationForm);
  }

  private String renderRegistrationForm(EntityConfiguration entityConfiguration) {
    return RegistratonFormRenderer.render(
        new Model(
            vfsConfirmation,
            memberId,
            entityConfiguration.orgName(),
            contactEmail,
            issuerUri,
            environment,
            entityConfiguration.scopes(),
            entityConfiguration.jwks()));
  }

  private void parameterValidation() {
    if (environment == Environment.PU) {
      if (vfsConfirmation == null || vfsConfirmation.isBlank()) {
        logger.atError().log("Verfahrensschlüssel is required for production (PU) environment");
        throw new RuntimeException();
      }
    } else {
      if (contactEmail == null || contactEmail.isBlank()) {
        logger.atError().log("contact email is required for test environments");
        throw new RuntimeException();
      }
    }
  }

  private EntityConfiguration fetchEntityConfiguration() {

    final var entityConfigurationUri = issuerUri.resolve("/.well-known/openid-federation");

    try {
      final HttpClient.Builder httpClientBuilder = HttpClient.newBuilder();
      withProxy(httpClientBuilder);

      final HttpClient httpClient = httpClientBuilder.build();

      final HttpRequest.Builder requestBuilder =
          HttpRequest.newBuilder(entityConfigurationUri).GET();

      withRequestExtraHeaders(requestBuilder);

      final var req = requestBuilder.build();

      var res = httpClient.send(req, BodyHandlers.ofString());
      if (res.statusCode() != 200) {
        throw new RuntimeException(
            "failed to retrieve entity configuration from '%s', status: %d"
                .formatted(entityConfigurationUri, res.statusCode()));
      }

      logger.atInfo().log("retrieved entity configuration from '{}'", entityConfigurationUri);

      var entityStatement = EntityStatementJWS.parse(res.body());
      return new EntityConfiguration(
          entityStatement.body().sub(),
          entityStatement.body().jwks(),
          entityStatement.body().metadata().federationEntity().name(),
          parseScopes(entityStatement.body().metadata().openIdRelyingParty().scope()));

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("unreachable");
    } catch (Exception e) {
      logger
          .atError()
          .setCause(e)
          .log("failed to fetch entity configuration from '{}'", entityConfigurationUri);
      if (e instanceof RuntimeException re) {
        throw re;
      } else {
        throw new RuntimeException(e);
      }
    }
  }

  private void withProxy(HttpClient.Builder builder) {
    if (proxy == null || proxy.isBlank()) {
      return;
    }

    if ("default".equalsIgnoreCase(proxy)) {
      builder.proxy(ProxySelector.getDefault());
      logger.atInfo().log("Setting default system proxy");
      return;
    }

    var proxyParts = proxy.split(":");
    if (proxyParts.length == 2) {
      try {
        var host = proxyParts[0];
        var port = Integer.parseInt(proxyParts[1]);

        logger.atInfo().log("Setting proxy to '{}:{}'", host, port);
        builder.proxy(ProxySelector.of(new InetSocketAddress(host, port)));
      } catch (final NumberFormatException e) {
        logger.atError().log("Invalid proxy port: " + proxyParts[1]);
        throw new RuntimeException("Invalid proxy port: " + proxyParts[1]);
      }
    } else {
      throw new IllegalArgumentException("Invalid proxy format. Expected format: host:port");
    }
  }

  private void withRequestExtraHeaders(HttpRequest.Builder builder) {
    if (httpHeaders == null) {
      return;
    }

    for (var h : httpHeaders) {
      var headerParts = h.split(":", 2);
      if (headerParts.length != 2) {
        throw new IllegalArgumentException(
            "Invalid header format, got: '%s' expected '<name>:<value>'".formatted(h));
      }

      var name = headerParts[0].trim();
      var value = headerParts[1].trim();

      if (name.isEmpty() || value.isEmpty()) {

        throw new IllegalArgumentException(
            "Invalid header format, got: '%s' expected '<name>:<value>'".formatted(h));
      }

      builder.header(name, value);
    }
  }

  private List<Scope> parseScopes(String scopes) {
    return Arrays.stream(scopes.split(" "))
        .flatMap(
            s ->
                switch (s) {
                  // https://gemspec.gematik.de/docs/gemSpec/gemSpec_IDP_Sek/gemSpec_IDP_Sek_V2.3.0/index.html#A_22989-01
                  case "urn:telematik:geburtsdatum" -> Stream.of(Scope.DATE_OF_BIRTH);
                  case "urn:telematik:alter" -> Stream.of(Scope.AGE);
                  case "urn:telematik:display_name" -> Stream.of(Scope.DISPLAY_NAME);
                  case "urn:telematik:given_name" -> Stream.of(Scope.FIRST_NAME);
                  case "urn:telematik:family_name" -> Stream.of(Scope.LAST_NAME);
                  case "urn:telematik:geschlecht" -> Stream.of(Scope.GENDER);
                  case "urn:telematik:email" -> Stream.of(Scope.EMAIL);
                  case "urn:telematik:versicherter" -> Stream.of(Scope.INSURED_PERSON);
                  default -> Stream.empty();
                })
        .toList();
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

  record EntityConfiguration(String sub, JWKSet jwks, String orgName, List<Scope> scopes) {}
}
