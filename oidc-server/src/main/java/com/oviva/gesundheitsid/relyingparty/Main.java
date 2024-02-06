package com.oviva.gesundheitsid.relyingparty;

import com.oviva.gesundheitsid.auth.AuthenticationFlow;
import com.oviva.gesundheitsid.fedclient.FederationMasterClientImpl;
import com.oviva.gesundheitsid.fedclient.api.CachedFederationApiClient;
import com.oviva.gesundheitsid.fedclient.api.FederationApiClientImpl;
import com.oviva.gesundheitsid.fedclient.api.InMemoryCacheImpl;
import com.oviva.gesundheitsid.fedclient.api.JavaHttpClient;
import com.oviva.gesundheitsid.fedclient.api.OpenIdClient;
import com.oviva.gesundheitsid.relyingparty.cfg.Config;
import com.oviva.gesundheitsid.relyingparty.cfg.ConfigProvider;
import com.oviva.gesundheitsid.relyingparty.cfg.EnvConfigProvider;
import com.oviva.gesundheitsid.relyingparty.fed.FederationConfig;
import com.oviva.gesundheitsid.relyingparty.poc.GematikHeaderDecoratorHttpClient;
import com.oviva.gesundheitsid.relyingparty.svc.InMemoryCodeRepo;
import com.oviva.gesundheitsid.relyingparty.svc.InMemorySessionRepo;
import com.oviva.gesundheitsid.relyingparty.svc.KeyStore;
import com.oviva.gesundheitsid.relyingparty.svc.TokenIssuerImpl;
import com.oviva.gesundheitsid.relyingparty.ws.App;
import com.oviva.gesundheitsid.util.JwksUtils;
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

  public static void main(String[] args) throws ExecutionException, InterruptedException {

    var main = new Main();
    main.run(new EnvConfigProvider("OIDC_SERVER", System::getenv));
  }

  public void run(ConfigProvider configProvider) throws ExecutionException, InterruptedException {
    logger.atInfo().log("\n" + BANNER);

    var baseUri = URI.create("https://t.oviva.io");
    var validRedirectUris =
        List.of(URI.create("https://idp-test.oviva.io/auth/realms/master/broker/oidc/endpoint"));

    var supportedResponseTypes = List.of("code");

    var port =
        configProvider.get("port").stream().mapToInt(Integer::parseInt).findFirst().orElse(1234);
    var config =
        new Config(
            port,
            baseUri, // TOOD: hardcoded :)
            // configProvider.get("base_uri").map(URI::create).orElse(URI.create("http://localhost:"
            // + port)),
            supportedResponseTypes,
            validRedirectUris // TODO: hardcoded :)

            //                        configProvider.get("redirect_uris").stream()
            //                            .flatMap(Strings::mustParseCommaList)
            //                            .map(URI::create)
            //                            .toList()
            );

    var keyStore = new KeyStore();
    var tokenIssuer = new TokenIssuerImpl(config.baseUri(), keyStore, new InMemoryCodeRepo());
    var sessionRepo = new InMemorySessionRepo();

    // setup your environment, your own issuer MUST serve a _valid_ and _trusted_ entity
    // configuration
    // see: https://wiki.gematik.de/pages/viewpage.action?pageId=544316583
    var fedmaster = URI.create("https://app-test.federationmaster.de");

    // TODO replace with `baseUri`
    var self = URI.create("https://idp-test.oviva.io/auth/realms/master/ehealthid");

    var authFlow = buildAuthFlow(baseUri, fedmaster);

    // TODO make path configurable
    var relyingPartyEncryptionJwks = JwksUtils.load(Path.of("./relying-party-enc_jwks.json"));
    var relyingPartySigningJwks = JwksUtils.load(Path.of("./relying-party-sig_jwks.json"));

    var federationConfig =
        FederationConfig.create()
            .sub(baseUri)
            .iss(baseUri)
            .appName("Oviva Direkt")
            .federationMaster(fedmaster)
            .entitySigningKey(relyingPartySigningJwks.getKeys().get(0).toECKey())
            .entitySigningKeys(relyingPartySigningJwks.toPublicJWKSet())
            .relyingPartyEncKeys(relyingPartyEncryptionJwks.toPublicJWKSet())

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

  private AuthenticationFlow buildAuthFlow(URI selfIssuer, URI fedmaster) {

    // path to the JWKS containing the private keys to decrypt ID tokens, the public part
    // is in your entity configuration
    var relyingPartyEncryptionJwks = JwksUtils.load(Path.of("./relying-party-enc_jwks.json"));

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
        selfIssuer, fedmasterClient, openIdClient, relyingPartyEncryptionJwks::getKeyByKeyId);
  }
}
