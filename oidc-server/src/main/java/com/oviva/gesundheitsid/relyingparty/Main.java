package com.oviva.gesundheitsid.relyingparty;

import com.oviva.gesundheitsid.relyingparty.cfg.Config;
import com.oviva.gesundheitsid.relyingparty.cfg.ConfigProvider;
import com.oviva.gesundheitsid.relyingparty.cfg.EnvConfigProvider;
import com.oviva.gesundheitsid.relyingparty.svc.InMemoryCodeRepo;
import com.oviva.gesundheitsid.relyingparty.svc.InMemorySessionRepo;
import com.oviva.gesundheitsid.relyingparty.svc.KeyStore;
import com.oviva.gesundheitsid.relyingparty.svc.TokenIssuerImpl;
import com.oviva.gesundheitsid.relyingparty.ws.App;
import jakarta.ws.rs.SeBootstrap;
import jakarta.ws.rs.SeBootstrap.Configuration;
import java.net.URI;
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

    var instance =
        SeBootstrap.start(
                new App(config, sessionRepo, keyStore, tokenIssuer),
                Configuration.builder().host("0.0.0.0").port(config.port()).build())
            .toCompletableFuture()
            .get();

    var localUri = instance.configuration().baseUri();
    logger.atInfo().addKeyValue("local_addr", localUri).log("Magic at {}", config.baseUri());

    // wait forever
    Thread.currentThread().join();
  }
}
