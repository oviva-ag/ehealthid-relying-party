package com.oviva.ehealthid.relyingparty;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
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
import com.oviva.ehealthid.relyingparty.svc.AuthService;
import com.oviva.ehealthid.relyingparty.svc.CaffeineCodeRepo;
import com.oviva.ehealthid.relyingparty.svc.CaffeineSessionRepo;
import com.oviva.ehealthid.relyingparty.svc.ClientAuthenticator;
import com.oviva.ehealthid.relyingparty.svc.CodeRepo;
import com.oviva.ehealthid.relyingparty.svc.KeyStore;
import com.oviva.ehealthid.relyingparty.svc.SessionRepo;
import com.oviva.ehealthid.relyingparty.svc.SessionRepo.Session;
import com.oviva.ehealthid.relyingparty.svc.TokenIssuer.Code;
import com.oviva.ehealthid.relyingparty.svc.TokenIssuerImpl;
import com.oviva.ehealthid.relyingparty.util.DiscoveryJwkSetSource;
import com.oviva.ehealthid.relyingparty.ws.App;
import com.oviva.ehealthid.relyingparty.ws.ManagementApp;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import jakarta.ws.rs.SeBootstrap;
import jakarta.ws.rs.SeBootstrap.Configuration;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main implements AutoCloseable {

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

  private SeBootstrap.Instance server;
  private SeBootstrap.Instance managementServer;

  private CountDownLatch shutdown = new CountDownLatch(1);

  public Main(ConfigProvider configProvider) {
    this.configProvider = configProvider;
  }

  public static void main(String[] args) throws Exception {

    try (var main = new Main(new EnvConfigProvider(CONFIG_PREFIX, System::getenv))) {
      main.run();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      logger.atError().setCause(e).log("server unexpectedly stopped");
      throw e;
    }
  }

  public void run() throws ExecutionException, InterruptedException {
    start();

    // wait for shutdown
    shutdown.await();
  }

  public URI baseUri() {
    return server.configuration().baseUri();
  }

  public URI managementBaseUri() {
    return managementServer.configuration().baseUri();
  }

  public void start() throws ExecutionException, InterruptedException {

    logger.atInfo().log("\n" + BANNER);
    var pkg = Main.class.getPackage();
    logger.atInfo().log(
        "booting '{}' at version '{}'",
        pkg.getImplementationTitle(),
        pkg.getImplementationVersion());

    var configReader = new ConfigReader(configProvider);

    var config = configReader.read();

    var keyStore = new KeyStore();
    var meterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    var codeRepo = buildCodeRepo(config.codeStoreConfig(), meterRegistry);
    var tokenIssuer = new TokenIssuerImpl(config.baseUri(), keyStore, codeRepo);
    var sessionRepo = buildSessionRepo(config.sessionStore(), meterRegistry);

    var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    var authFlow =
        buildAuthFlow(
            config.baseUri(),
            config.federation().federationMaster(),
            config.federation().relyingPartyEncKeys(),
            httpClient);

    var jwkSource =
        JWKSourceBuilder.create(new DiscoveryJwkSetSource<>(httpClient, config.idpDiscoveryUri()))
            .refreshAheadCache(true)
            .build();

    var clientAuthenticator = new ClientAuthenticator(jwkSource, config.baseUri());

    var authService =
        new AuthService(
            config.baseUri(),
            config.relyingParty(),
            config.federation(),
            sessionRepo,
            tokenIssuer,
            authFlow);

    server =
        SeBootstrap.start(
                new App(config, keyStore, tokenIssuer, clientAuthenticator, authService),
                Configuration.builder().host(config.host()).port(config.port()).build())
            .toCompletableFuture()
            .get();

    var localUri = server.configuration().baseUri();
    logger.atInfo().log("Magic at {} ({})", config.baseUri(), localUri);

    managementServer =
        SeBootstrap.start(
                new ManagementApp(meterRegistry),
                Configuration.builder().host(config.host()).port(config.managementPort()).build())
            .toCompletableFuture()
            .get();

    var metricsLocalUri = managementServer.configuration().baseUri();
    logger.atInfo().log("Management Server at {} ({})", config.baseUri(), metricsLocalUri);
  }

  private AuthenticationFlow buildAuthFlow(
      URI selfIssuer, URI fedmaster, JWKSet encJwks, HttpClient httpClient) {

    // set up the file `.env.properties` to provide the X-Authorization header for the Gematik
    // test environment
    // see: https://wiki.gematik.de/display/IDPKB/Fachdienste+Test-Umgebungen
    var fedHttpClient = new GematikHeaderDecoratorHttpClient(new JavaHttpClient(httpClient));

    // setup as needed
    var clock = Clock.systemUTC();
    var ttl = Duration.ofMinutes(5);

    var federationApiClient =
        new CachedFederationApiClient(
            new FederationApiClientImpl(fedHttpClient),
            new InMemoryCacheImpl<>(clock, ttl),
            new InMemoryCacheImpl<>(clock, ttl),
            new InMemoryCacheImpl<>(clock, ttl));

    var fedmasterClient = new FederationMasterClientImpl(fedmaster, federationApiClient, clock);
    var openIdClient = new OpenIdClient(fedHttpClient);

    return new AuthenticationFlow(
        selfIssuer, fedmasterClient, openIdClient, encJwks::getKeyByKeyId);
  }

  private SessionRepo buildSessionRepo(
      SessionStoreConfig config, PrometheusMeterRegistry meterRegistry) {
    Cache<String, Session> store = buildCache(config.ttl(), config.maxEntries());

    CaffeineCacheMetrics<String, Session, Cache<String, Session>> metrics =
        new CaffeineCacheMetrics<>(store, "sessionCache", null);
    metrics.bindTo(meterRegistry);

    return new CaffeineSessionRepo(store, config.ttl());
  }

  private CodeRepo buildCodeRepo(CodeStoreConfig config, PrometheusMeterRegistry meterRegistry) {
    Cache<String, Code> store = buildCache(config.ttl(), config.maxEntries());

    CaffeineCacheMetrics<String, Code, Cache<String, Code>> metrics =
        new CaffeineCacheMetrics<>(store, "codeCache", null);
    metrics.bindTo(meterRegistry);

    return new CaffeineCodeRepo(store);
  }

  private <T> Cache<String, T> buildCache(Duration ttl, int maxSize) {
    return Caffeine.newBuilder()
        .expireAfter(new AfterCreatedExpiry<>(ttl.toNanos()))
        .maximumSize(maxSize)
        .recordStats()
        .build();
  }

  @Override
  public void close() throws Exception {
    server.stop().toCompletableFuture().get(10, TimeUnit.SECONDS);
    shutdown.countDown();
  }
}
