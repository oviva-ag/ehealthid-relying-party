package ehealthid.relyingparty;

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
  private final ConfigProvider configProvider;

  public Main(ConfigProvider configProvider) {
    this.configProvider = configProvider;
  }

  public static void main(String[] args) throws ExecutionException, InterruptedException {

    var main = new Main(new EnvConfigProvider("OIDC_SERVER", System::getenv));
    main.run();
  }

  public void run() throws ExecutionException, InterruptedException {

    logger.atInfo().log("\n" + BANNER);

    var federationEncJwksPath = loadJwks("federation_enc_jwks_path");
    var federationSigJwksPath = loadJwks("federation_sig_jwks_path");

    var validRedirectUris = loadAllowedRedirectUrls();

    // TODO load from config
    var baseUri = URI.create("https://t.oviva.io");

    var supportedResponseTypes = List.of("code");

    var port = getPortConfig();

    var config = new RelyingPartyConfig(port, baseUri, supportedResponseTypes, validRedirectUris);

    var keyStore = new KeyStore();
    var tokenIssuer = new TokenIssuerImpl(config.baseUri(), keyStore, new InMemoryCodeRepo());
    var sessionRepo = new InMemorySessionRepo();

    // TODO
    // setup your environment, your own issuer MUST serve a _valid_ and _trusted_ entity
    // configuration
    // see: https://wiki.gematik.de/pages/viewpage.action?pageId=544316583
    var fedmaster = URI.create("https://app-test.federationmaster.de");

    // TODO replace with `baseUri`
    var federationIssuer = URI.create("https://idp-test.oviva.io/auth/realms/master/ehealthid");

    var authFlow = buildAuthFlow(baseUri, fedmaster, federationEncJwksPath);

    var federationConfig =
        FederationConfig.create()
            .sub(baseUri)
            .iss(baseUri)
            .appName("Oviva Direkt")
            .federationMaster(fedmaster)
            .entitySigningKey(federationSigJwksPath.getKeys().get(0).toECKey())
            .entitySigningKeys(federationSigJwksPath.toPublicJWKSet())
            .relyingPartyEncKeys(federationEncJwksPath.toPublicJWKSet())

            // TODO: bump up to hours, once we're confident it's correct ;)
            // the spec says ~1 day
            .ttl(Duration.ofMinutes(5))
            .redirectUris(List.of(baseUri.resolve("/auth/callback").toString()))
            .build();

    var instance =
        SeBootstrap.start(
                new App(config, federationConfig, sessionRepo, keyStore, tokenIssuer, authFlow),
                Configuration.builder().host("0.0.0.0").port(config.port()).build())
            .toCompletableFuture()
            .get();

    var localUri = instance.configuration().baseUri();
    logger.atInfo().addKeyValue("local_addr", localUri).log("Magic at {}", config.baseUri());

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

    var redirectUris =
        configProvider.get("redirect_uris").stream()
            .flatMap(Strings::mustParseCommaList)
            .map(URI::create)
            .toList();

    if (!redirectUris.isEmpty()) {
      return redirectUris;
    }

    // TODO: hardcoded
    return List.of(URI.create("https://idp-test.oviva.io/auth/realms/master/broker/oidc/endpoint"));
  }
}
