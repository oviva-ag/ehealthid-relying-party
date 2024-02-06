package com.oviva.ehealthid.relyingparty;

import com.nimbusds.jose.jwk.JWKSet;
import com.oviva.ehealthid.auth.AuthenticationFlow;
import com.oviva.ehealthid.fedclient.FederationMasterClientImpl;
import com.oviva.ehealthid.fedclient.api.CachedFederationApiClient;
import com.oviva.ehealthid.fedclient.api.FederationApiClientImpl;
import com.oviva.ehealthid.fedclient.api.InMemoryCacheImpl;
import com.oviva.ehealthid.fedclient.api.JavaHttpClient;
import com.oviva.ehealthid.fedclient.api.OpenIdClient;
import com.oviva.ehealthid.relyingparty.cfg.ConfigProvider;
import com.oviva.ehealthid.relyingparty.cfg.EnvConfigProvider;
import com.oviva.ehealthid.relyingparty.cfg.RelyingPartyConfig;
import com.oviva.ehealthid.relyingparty.fed.FederationConfig;
import com.oviva.ehealthid.relyingparty.poc.GematikHeaderDecoratorHttpClient;
import com.oviva.ehealthid.relyingparty.svc.InMemoryCodeRepo;
import com.oviva.ehealthid.relyingparty.svc.InMemorySessionRepo;
import com.oviva.ehealthid.relyingparty.svc.KeyStore;
import com.oviva.ehealthid.relyingparty.svc.TokenIssuerImpl;
import com.oviva.ehealthid.relyingparty.util.Strings;
import com.oviva.ehealthid.relyingparty.ws.App;
import com.oviva.ehealthid.util.JwksUtils;
import jakarta.ws.rs.SeBootstrap;
import jakarta.ws.rs.SeBootstrap.Configuration;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);
  private static final String BANNER =
      """
          ____       _
         / __ \\_  __(_)  _____ _
        / /_/ / |/ / / |/ / _ `/
        \\____/|___/_/|___/\\_,_/
             GesundheitsID OpenID Connect Relying-Party
        """;
  private static final String CONFIG_PREFIX = "EHEALTHID_RP";
  private final ConfigProvider configProvider;

  public Main(ConfigProvider configProvider) {
    this.configProvider = configProvider;
  }

  public static void main(String[] args) throws ExecutionException, InterruptedException {

    var main = new Main(new EnvConfigProvider(CONFIG_PREFIX, System::getenv));
    main.run();
  }

  public void run() throws ExecutionException, InterruptedException {

    logger.atInfo().log("\n" + BANNER);

    var federationEncJwksPath = loadJwks("federation_enc_jwks_path");
    var federationSigJwksPath = loadJwks("federation_sig_jwks_path");

    var validRedirectUris = loadAllowedRedirectUrls();

    var baseUri =
        configProvider
            .get("base_uri")
            .map(URI::create)
            .orElseThrow(() -> new IllegalArgumentException("no 'base_uri' configured"));

    var host = configProvider.get("host").orElse("0.0.0.0");
    var port = getPortConfig();

    var supportedResponseTypes = List.of("code");

    var config = new RelyingPartyConfig(port, baseUri, supportedResponseTypes, validRedirectUris);

    var keyStore = new KeyStore();
    var tokenIssuer = new TokenIssuerImpl(config.baseUri(), keyStore, new InMemoryCodeRepo());
    var sessionRepo = new InMemorySessionRepo();

    // TODO
    // setup your environment, your own issuer MUST serve a _valid_ and _trusted_ entity
    // configuration
    // see: https://wiki.gematik.de/pages/viewpage.action?pageId=544316583
    var fedmaster =
        configProvider
            .get("federation_master")
            .map(URI::create)
            .orElse(URI.create("https://app-test.federationmaster.de"));

    var appName =
        configProvider
            .get("app_name")
            .orElseThrow(() -> new IllegalArgumentException("missing 'app_name' configuration"));

    var entityStatementTtl =
        configProvider.get("es_ttl").map(Duration::parse).orElse(Duration.ofHours(1));

    var authFlow = buildAuthFlow(baseUri, fedmaster, federationEncJwksPath);

    var scopes =
        configProvider
            .get("scopes")
            .or(
                () ->
                    Optional.of(
                        "openid,urn:telematik:email,urn:telematik:versicherter,urn:telematik:display_name"))
            .stream()
            .flatMap(Strings::mustParseCommaList)
            .toList();

    var federationConfig =
        FederationConfig.create()
            .sub(baseUri)
            .iss(baseUri)
            .appName(appName)
            .federationMaster(fedmaster)
            .entitySigningKey(federationSigJwksPath.getKeys().get(0).toECKey())
            .entitySigningKeys(federationSigJwksPath.toPublicJWKSet())
            .relyingPartyEncKeys(federationEncJwksPath.toPublicJWKSet())
            .ttl(entityStatementTtl)
            .scopes(scopes)
            .redirectUris(List.of(baseUri.resolve("/auth/callback").toString()))
            .build();

    var instance =
        SeBootstrap.start(
                new App(config, federationConfig, sessionRepo, keyStore, tokenIssuer, authFlow),
                Configuration.builder().host(host).port(config.port()).build())
            .toCompletableFuture()
            .get();

    var localUri = instance.configuration().baseUri();
    logger.atInfo().log("Magic at {} ({})", config.baseUri(), localUri);

    // wait forever
    Thread.currentThread().join();
  }

  private int getPortConfig() {
    return configProvider.get("port").stream().mapToInt(Integer::parseInt).findFirst().orElse(1234);
  }

  private JWKSet loadJwks(String configName) {

    var path =
        configProvider
            .get(configName)
            .map(Path::of)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "missing jwks path for '%s'".formatted(configName)));

    return JwksUtils.load(path);
  }

  private AuthenticationFlow buildAuthFlow(URI selfIssuer, URI fedmaster, JWKSet encJwks) {

    // setup the file `.env.properties` to provide the X-Authorization header for the Gematik
    // test environment
    // see: https://wiki.gematik.de/display/IDPKB/Fachdienste+Test-Umgebungen
    var httpClient = new GematikHeaderDecoratorHttpClient(new JavaHttpClient());

    // setup as needed
    var clock = Clock.systemUTC();
    var ttl = Duration.ofMinutes(5);

    var federationApiClient =
        new CachedFederationApiClient(
            new FederationApiClientImpl(httpClient),
            new InMemoryCacheImpl<>(clock, ttl),
            new InMemoryCacheImpl<>(clock, ttl),
            new InMemoryCacheImpl<>(clock, ttl));

    var fedmasterClient = new FederationMasterClientImpl(fedmaster, federationApiClient, clock);
    var openIdClient = new OpenIdClient(httpClient);

    return new AuthenticationFlow(
        selfIssuer, fedmasterClient, openIdClient, encJwks::getKeyByKeyId);
  }

  private List<URI> loadAllowedRedirectUrls() {
    return configProvider.get("redirect_uris").stream()
        .flatMap(Strings::mustParseCommaList)
        .map(URI::create)
        .toList();
  }
}
