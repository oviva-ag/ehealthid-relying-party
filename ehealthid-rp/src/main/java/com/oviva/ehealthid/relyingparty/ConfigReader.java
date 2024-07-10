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

  public static final String CONFIG_FEDERATION_ENTITY_STATEMENT_JWKS_PATH =
      "federation_es_jwks_path";
  public static final String CONFIG_BASE_URI = "base_uri";
  public static final String CONFIG_HOST = "host";
  public static final String CONFIG_PORT = "port";
  public static final String CONFIG_MANAGEMENT_PORT = "management_port";
  public static final String CONFIG_REDIRECT_URIS = "redirect_uris";

  public static final String CONFIG_IDP_DISCOVERY_URI = "idp_discovery_uri";
  public static final String CONFIG_FEDERATION_MASTER = "federation_master";
  public static final String CONFIG_ES_TTL = "es_ttl";
  public static final String CONFIG_APP_NAME = "app_name";
  public static final String CONFIG_SCOPES = "scopes";

  public static final String CONFIG_SESSION_STORE_TTL = "session_store_ttl";
  public static final String CONFIG_SESSION_STORE_MAX_ENTRIES = "session_store_max_entries";

  public static final String CONFIG_CODE_STORE_TTL = "code_store_ttl";
  public static final String CONFIG_CODE_STORE_MAX_ENTRIES = "code_store_max_entries";

  private final ConfigProvider configProvider;

  public ConfigReader(ConfigProvider configProvider) {
    this.configProvider = configProvider;
  }

  public Config read() {

    var federationEntityStatementJwksPath = loadJwks(CONFIG_FEDERATION_ENTITY_STATEMENT_JWKS_PATH);

    var baseUri =
        configProvider
            .get(CONFIG_BASE_URI)
            .map(URI::create)
            .orElseThrow(() -> new IllegalArgumentException("no 'base_uri' configured"));

    var idpDiscoveryUri =
        configProvider
            .get(CONFIG_IDP_DISCOVERY_URI)
            .map(URI::create)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "no '%s' configured".formatted(CONFIG_IDP_DISCOVERY_URI)));

    var host = configProvider.get(CONFIG_HOST).orElse("0.0.0.0");
    var port = getPortConfig(CONFIG_PORT, 1234);
    var managementPort = getPortConfig(CONFIG_MANAGEMENT_PORT, 1235);

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

            // safety, remove the private key as we don't need it here
            .entitySigningKeys(federationEntityStatementJwksPath.toPublicJWKSet())
            .entitySigningKey(federationEntityStatementJwksPath.getKeys().get(0).toECKey())
            .ttl(entityStatementTtl)
            .scopes(getScopes())
            .redirectUris(List.of(baseUri.resolve("/auth/callback").toString()))
            .build();

    var supportedResponseTypes = List.of("code");

    var relyingPartyConfig =
        new RelyingPartyConfig(supportedResponseTypes, loadAllowedRedirectUrls());

    return new Config(
        relyingPartyConfig,
        federationConfig,
        host,
        port,
        managementPort,
        baseUri,
        idpDiscoveryUri,
        sessionStoreConfig(),
        codeStoreConfig());
  }

  private SessionStoreConfig sessionStoreConfig() {
    var ttl = getDurationOrDefault(CONFIG_SESSION_STORE_TTL, Duration.ofMinutes(20));
    var maxEntries = getIntOrDefault(CONFIG_SESSION_STORE_MAX_ENTRIES, 1000);
    return new SessionStoreConfig(ttl, maxEntries);
  }

  private CodeStoreConfig codeStoreConfig() {
    var ttl = getDurationOrDefault(CONFIG_CODE_STORE_TTL, Duration.ofMinutes(5));
    var maxEntries = getIntOrDefault(CONFIG_CODE_STORE_MAX_ENTRIES, 1000);
    return new CodeStoreConfig(ttl, maxEntries);
  }

  private List<URI> loadAllowedRedirectUrls() {
    return configProvider.get(CONFIG_REDIRECT_URIS).stream()
        .flatMap(Strings::mustParseCommaList)
        .map(URI::create)
        .toList();
  }

  private Duration getDurationOrDefault(String config, Duration defaultValue) {
    return configProvider.get(config).map(Duration::parse).orElse(defaultValue);
  }

  private int getIntOrDefault(String config, int defaultValue) {
    return configProvider.get(config).map(Integer::parseInt).orElse(defaultValue);
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

  private int getPortConfig(String configPort, int defaultValue) {
    return configProvider.get(configPort).stream()
        .mapToInt(Integer::parseInt)
        .findFirst()
        .orElse(defaultValue);
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
      int managementPort,
      URI baseUri,
      URI idpDiscoveryUri,
      SessionStoreConfig sessionStore,
      CodeStoreConfig codeStoreConfig) {}

  public record SessionStoreConfig(Duration ttl, int maxEntries) {}

  public record CodeStoreConfig(Duration ttl, int maxEntries) {}
}
