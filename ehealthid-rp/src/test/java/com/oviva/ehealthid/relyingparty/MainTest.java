package com.oviva.ehealthid.relyingparty;

import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.oviva.ehealthid.relyingparty.cfg.ConfigProvider;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

@WireMockTest
class MainTest {

  private static final String DISCOVERY_PATH = "/.well-known/openid-configuration";
  private static final String FEDERATION_CONFIG_PATH = "/.well-known/openid-federation";
  private static final String JWKS_PATH = "/jwks.json";

  private static final ExecutorService executor = Executors.newSingleThreadExecutor();

  @AfterAll
  static void afterAll() {
    executor.shutdownNow();
  }

  @Test
  void run(WireMockRuntimeInfo wm) throws Exception {

    var discoveryUri = URI.create(wm.getHttpBaseUrl()).resolve(DISCOVERY_PATH);

    var config =
        configFromProperties(
            """
    federation_enc_jwks_path=src/test/resources/fixtures/example_enc_jwks.json
    federation_sig_jwks_path=src/test/resources/fixtures/example_sig_jwks.json
    base_uri=%s
    idp_discovery_uri=%s
    app_name=Awesome DiGA
    port=0
    """
                .formatted(wm.getHttpBaseUrl(), discoveryUri));

    var main = new Main(config);

    // when
    main.start();

    // then
    var baseUri = main.baseUri();
    tryPing(baseUri.resolve(DISCOVERY_PATH));
    tryPing(baseUri.resolve(JWKS_PATH));
    tryPing(baseUri.resolve(FEDERATION_CONFIG_PATH));

    main.close();
  }

  private ConfigProvider configFromProperties(String s) {
    var props = new Properties();
    try {
      props.load(new StringReader(s));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return new StaticConfig(props);
  }

  private void tryPing(URI uri) throws IOException, InterruptedException {

    var client = HttpClient.newHttpClient();
    for (int i = 0; i < 100; i++) {
      var req = HttpRequest.newBuilder(uri).GET().build();

      var res = client.send(req, BodyHandlers.ofByteArray());
      if (res.statusCode() == 200) {
        return;
      }
      Thread.sleep(Duration.ofMillis(500).toMillis());
    }
    fail();
  }

  record StaticConfig(Map<Object, Object> values) implements ConfigProvider {

    @Override
    public Optional<String> get(String name) {
      return Optional.ofNullable(values.get(name)).map(Object::toString);
    }
  }
}
