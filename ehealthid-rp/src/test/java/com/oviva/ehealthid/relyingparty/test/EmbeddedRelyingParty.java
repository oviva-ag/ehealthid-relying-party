package com.oviva.ehealthid.relyingparty.test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.Stubbing;
import com.oviva.ehealthid.relyingparty.Main;
import com.oviva.ehealthid.relyingparty.cfg.ConfigProvider;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

public class EmbeddedRelyingParty implements AutoCloseable {

  private static final String DISCOVERY_PATH = "/.well-known/openid-configuration";
  private Main application;
  private WireMockServer wireMockServer;

  public URI start() throws ExecutionException, InterruptedException {

    var options = WireMockConfiguration.options().dynamicPort();
    this.wireMockServer = new WireMockServer(options);
    wireMockServer.start();

    var discoveryUri = URI.create(wireMockServer.baseUrl()).resolve(DISCOVERY_PATH);

    var redirectUri = URI.create("https://myapp.example.com");

    var config =
        StaticConfig.fromRawProperties(
            """
                federation_enc_jwks_path=src/test/resources/fixtures/example_enc_jwks.json
                federation_sig_jwks_path=src/test/resources/fixtures/example_sig_jwks.json
                base_uri=%s
                idp_discovery_uri=%s
                redirect_uris=%s
                app_name=Awesome DiGA
                port=0
                management_port=0
                """
                .formatted(wireMockServer.baseUrl(), discoveryUri, redirectUri));

    this.application = new Main(config);

    application.start();

    return application.baseUri();
  }

  public URI baseUri() {
    return application.baseUri();
  }

  public URI managementBaseUri() {
    return application.managementBaseUri();
  }

  public Stubbing wireMockStubbing() {
    return wireMockServer;
  }

  @Override
  public void close() throws Exception {

    Exception cause = null;

    try {
      this.application.close();
    } catch (Exception e) {
      cause = e;
    }

    this.wireMockServer.stop();
    if (cause != null) {
      throw cause;
    }
  }

  record StaticConfig(Map<Object, Object> values) implements ConfigProvider {

    @Override
    public Optional<String> get(String name) {
      return Optional.ofNullable(values.get(name)).map(Object::toString);
    }

    public static ConfigProvider fromRawProperties(String s) {
      var props = new Properties();
      try {
        props.load(new StringReader(s));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      return new StaticConfig(props);
    }
  }
}
