package com.oviva.ehealthid.relyingparty;

import com.nimbusds.jose.jwk.JWKSet;
import com.oviva.ehealthid.relyingparty.cfg.ConfigProvider;
import com.oviva.ehealthid.relyingparty.cfg.RelyingPartyConfig;
import com.oviva.ehealthid.relyingparty.fed.FederationConfig;
import com.oviva.ehealthid.relyingparty.util.Strings;
import com.oviva.ehealthid.util.JwksUtils;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class ConfigReader {

  public static final String CONFIG_FEDERATION_ENC_JWKS_PATH = "federation_enc_jwks_path";
  public static final String CONFIG_FEDERATION_SIG_JWKS_PATH = "federation_sig_jwks_path";
  public static final String CONFIG_BASE_URI = "base_uri";
  public static final String CONFIG_HOST = "host";
  public static final String CONFIG_PORT = "port";
  public static final String CONFIG_REDIRECT_URIS = "redirect_uris";
  public static final String CONFIG_FEDERATION_MASTER = "federation_master";
  public static final String CONFIG_ES_TTL = "es_ttl";
  public static final String CONFIG_APP_NAME = "app_name";
  public static final String CONFIG_SCOPES = "scopes";

  private final ConfigProvider configProvider;

  public ConfigReader(ConfigProvider configProvider) {
    this.configProvider = configProvider;
  }

  public Config read() {

    var federationEncJwksPath = loadJwks(CONFIG_FEDERATION_ENC_JWKS_PATH);
    var federationSigJwksPath = loadJwks(CONFIG_FEDERATION_SIG_JWKS_PATH);

    var baseUri =
        configProvider
            .get(CONFIG_BASE_URI)
            .map(URI::create)
            .orElseThrow(() -> new IllegalArgumentException("no 'base_uri' configured"));

    var host = configProvider.get(CONFIG_HOST).orElse("0.0.0.0");
    var port = getPortConfig();

    var fedmaster =
        configProvider
            .get(CONFIG_FEDERATION_MASTER)
            .map(URI::create)
            .orElse(URI.create("https://app-test.federationmaster.de"));

    var appName =
        configProvider
            .get(CONFIG_APP_NAME)
            .orElseThrow(() -> new IllegalArgumentException("missing 'app_name' configuration"));

    var entityStatementTtl =
        configProvider.get(CONFIG_ES_TTL).map(Duration::parse).orElse(Duration.ofHours(1));

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
            .scopes(getScopes())
            .redirectUris(List.of(baseUri.resolve("/auth/callback").toString()))
            .build();

    var supportedResponseTypes = List.of("code");

    var relyingPartyConfig =
        new RelyingPartyConfig(supportedResponseTypes, loadAllowedRedirectUrls());

    return new Config(relyingPartyConfig, federationConfig, host, port, baseUri);
  }

  private List<URI> loadAllowedRedirectUrls() {
    return configProvider.get(CONFIG_REDIRECT_URIS).stream()
        .flatMap(Strings::mustParseCommaList)
        .map(URI::create)
        .toList();
  }

  private List<String> getScopes() {

    return configProvider
        .get(CONFIG_SCOPES)
        .or(
            () ->
                Optional.of(
                    "openid,urn:telematik:email,urn:telematik:versicherter,urn:telematik:display_name"))
        .stream()
        .flatMap(Strings::mustParseCommaList)
        .toList();
  }

  private int getPortConfig() {
    return configProvider.get(CONFIG_PORT).stream()
        .mapToInt(Integer::parseInt)
        .findFirst()
        .orElse(1234);
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

  public record Config(
      RelyingPartyConfig relyingParty,
      FederationConfig federation,
      String host,
      int port,
      URI baseUri) {}
}
