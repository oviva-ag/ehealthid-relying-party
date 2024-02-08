package com.oviva.ehealthid.relyingparty;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nimbusds.jose.jwk.JWKSet;
import com.oviva.ehealthid.auth.AuthenticationFlow;
import com.oviva.ehealthid.fedclient.FederationMasterClientImpl;
import com.oviva.ehealthid.fedclient.api.CachedFederationApiClient;
import com.oviva.ehealthid.fedclient.api.FederationApiClientImpl;
import com.oviva.ehealthid.fedclient.api.InMemoryCacheImpl;
import com.oviva.ehealthid.fedclient.api.JavaHttpClient;
import com.oviva.ehealthid.fedclient.api.OpenIdClient;
import com.oviva.ehealthid.relyingparty.ConfigReader.CodeStoreConfig;
import com.oviva.ehealthid.relyingparty.ConfigReader.SessionStoreConfig;
import com.oviva.ehealthid.relyingparty.cfg.ConfigProvider;
import com.oviva.ehealthid.relyingparty.cfg.EnvConfigProvider;
import com.oviva.ehealthid.relyingparty.poc.GematikHeaderDecoratorHttpClient;
import com.oviva.ehealthid.relyingparty.svc.AfterCreatedExpiry;
import com.oviva.ehealthid.relyingparty.svc.CaffeineCodeRepo;
import com.oviva.ehealthid.relyingparty.svc.CaffeineSessionRepo;
import com.oviva.ehealthid.relyingparty.svc.CodeRepo;
import com.oviva.ehealthid.relyingparty.svc.KeyStore;
import com.oviva.ehealthid.relyingparty.svc.SessionRepo;
import com.oviva.ehealthid.relyingparty.svc.SessionRepo.Session;
import com.oviva.ehealthid.relyingparty.svc.TokenIssuer.Code;
import com.oviva.ehealthid.relyingparty.svc.TokenIssuerImpl;
import com.oviva.ehealthid.relyingparty.ws.App;
import jakarta.ws.rs.SeBootstrap;
import jakarta.ws.rs.SeBootstrap.Configuration;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
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
    var pkg = Main.class.getPackage();
    logger.atInfo().log(
        "booting '{}' at version '{}'",
        pkg.getImplementationTitle(),
        pkg.getImplementationVersion());

    var configReader = new ConfigReader(configProvider);

    var config = configReader.read();

    var keyStore = new KeyStore();
    var codeRepo = buildCodeRepo(config.codeStoreConfig());
    var tokenIssuer = new TokenIssuerImpl(config.baseUri(), keyStore, codeRepo);
    var sessionRepo = buildSessionRepo(config.sessionStore());

    var authFlow =
        buildAuthFlow(
            config.baseUri(),
            config.federation().federationMaster(),
            config.federation().relyingPartyEncKeys());

    var instance =
        SeBootstrap.start(
                new App(config, sessionRepo, keyStore, tokenIssuer, authFlow),
                Configuration.builder().host(config.host()).port(config.port()).build())
            .toCompletableFuture()
            .get();

    var localUri = instance.configuration().baseUri();
    logger.atInfo().log("Magic at {} ({})", config.baseUri(), localUri);

    // wait forever
    Thread.currentThread().join();
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

  private SessionRepo buildSessionRepo(SessionStoreConfig config) {
    Cache<String, Session> store = buildCache(config.ttl(), config.maxEntries());
    return new CaffeineSessionRepo(store, config.ttl());
  }

  private CodeRepo buildCodeRepo(CodeStoreConfig config) {
    Cache<String, Code> store = buildCache(config.ttl(), config.maxEntries());
    return new CaffeineCodeRepo(store);
  }

  private <T> Cache<String, T> buildCache(Duration ttl, int maxSize) {
    return Caffeine.newBuilder()
        .expireAfter(new AfterCreatedExpiry(ttl.toNanos()))
        .maximumSize(maxSize)
        .build();
  }
}
