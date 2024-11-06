package com.oviva.ehealthid.relyingparty.providers;

import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.oviva.ehealthid.relyingparty.cfg.ConfigProvider;
import com.oviva.ehealthid.relyingparty.util.KeyGenerator;
import com.oviva.ehealthid.util.JwksUtils;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BasicKeystoreProvider {

  private static final Logger logger = LoggerFactory.getLogger(BasicKeystoreProvider.class);

  public static final String CONFIG_FEDERATION_SIG_JWKS_PATH = "federation_es_jwks_path";
  public static final String CONFIG_OPENID_RP_SIG_JWKS_PATH = "openid_rp_sig_jwks_path";
  public static final String CONFIG_OPENID_RP_ENC_JWKS_PATH = "openid_rp_enc_jwks_path";
  public static final String CONFIG_OPENID_PROVIDER_SIG_JWKS_PATH = "openid_provider_sig_jwks_path";

  public static KeyStores load(ConfigProvider config) {
    var openIdProviderSigningKeys =
        loadJwks(config, CONFIG_OPENID_PROVIDER_SIG_JWKS_PATH)
            .orElseGet(
                () -> {
                  logger.atInfo().log(
                      "no key for {} configured, generating ephemeral key",
                      CONFIG_OPENID_PROVIDER_SIG_JWKS_PATH);
                  return new JWKSet(KeyGenerator.generateSigningKey());
                });

    return fromKeys(
        loadJwks(config, CONFIG_FEDERATION_SIG_JWKS_PATH).orElseThrow(),
        mustGetFirstKey(config, CONFIG_OPENID_RP_SIG_JWKS_PATH),
        mustGetFirstKey(config, CONFIG_OPENID_RP_ENC_JWKS_PATH),
        openIdProviderSigningKeys.getKeys().get(0).toECKey());
  }

  private static ECKey mustGetFirstKey(ConfigProvider configProvider, String config) {

    var loadedJwks = loadJwks(configProvider, config);

    var jwks =
        loadedJwks.orElseThrow(
            () -> new IllegalArgumentException("JWKSet of '%s' is empty".formatted(config)));

    var firstKey = jwks.getKeys().get(0);
    if (!(firstKey instanceof ECKey ecKey)) {
      throw new IllegalStateException(
          "First key in JWKS of '%s' is not an ECKey".formatted(config));
    }

    if (!ecKey.isPrivate()) {
      throw new IllegalStateException(
          "First key in JWKS of '%s' is missing private key".formatted(config));
    }

    debugLogJwk(config, ecKey);

    return ecKey;
  }

  private static Optional<JWKSet> loadJwks(ConfigProvider configProvider, String configName) {

    var config = configProvider.get(configName);
    if (config.isEmpty()) {
      return Optional.empty();
    }

    var path =
        config
            .map(Path::of)
            .orElseThrow(
                () -> new IllegalArgumentException("bad jwks path for '%s'".formatted(configName)));

    var jwks = JwksUtils.load(path);
    if (jwks.isEmpty()) {
      throw new IllegalArgumentException("JWKSet is present but has no contents");
    }

    return Optional.of(jwks);
  }

  private static void debugLogJwk(String purpose, JWK jwk) {
    logger
        .atDebug()
        .addKeyValue("kid", jwk.getKeyID())
        .addKeyValue("jwk", jwk.toJSONString())
        .log("{} signing key, kid={} jwk={}", purpose, jwk.getKeyID(), jwk.toJSONString());
  }

  /**
   * @param federationSigningKey the trusted keys of our party in the federation, the first key will
   *     be used for signing operations
   * @param openIdRelyingPartySigningKey signing key of our relying party, i.e. for mTLS client
   *     certificates
   * @param openIdRelyingPartyEncryptionKey encryption key of our relying party, i.e. for ephemeral
   *     JWE encryption of id_tokens
   * @param openIdProviderSigningKey signing key for our openIdProvider, the id_token issued by the
   *     relying party will be signed with it
   */
  public static KeyStores fromKeys(
      JWKSet federationSigningKey,
      ECKey openIdRelyingPartySigningKey,
      ECKey openIdRelyingPartyEncryptionKey,
      ECKey openIdProviderSigningKey) {

    var federationJwks = federationSigningKey.getKeys().stream().map(JWK::toECKey).toList();

    var rpSigningKeyProvider = cachedSigningKeyProvider(openIdRelyingPartySigningKey);

    return new StaticKeyStores(
        rpSigningKeyProvider,
        openIdRelyingPartyEncryptionKey,
        openIdProviderSigningKey,
        federationJwks);
  }

  private static Function<URI, ECKey> cachedSigningKeyProvider(ECKey openIdRelyingPartySigningKey) {

    var cache = new ConcurrentHashMap<URI, ECKey>();

    return uri ->
        cache.compute(
            uri,
            (u, key) -> {
              if (key == null) {
                key = KeyGenerator.addCertificate(openIdRelyingPartySigningKey, u);
              }
              return key;
            });
  }

  static class StaticKeyStores implements KeyStores {

    private final Function<URI, ECKey> rpSigKeysProvider;
    private final ECKey rpEncKey;
    private final ECKey opKey;

    private final List<ECKey> federationSigKey;

    StaticKeyStores(
        Function<URI, ECKey> rpSigKeysProvider,
        ECKey rpEncKey,
        ECKey opKey,
        List<ECKey> federationSigKey) {
      this.rpSigKeysProvider = rpSigKeysProvider;
      this.rpEncKey = rpEncKey;
      this.opKey = opKey;
      this.federationSigKey = federationSigKey;
    }

    @Override
    public KeyStore openIdProviderJwksKeystore() {
      return () -> List.of(opKey);
    }

    @Override
    public KeyStore federationSigJwksKeystore() {
      return () -> federationSigKey;
    }

    @Override
    public KeyStore relyingPartyEncJwksKeystore() {
      return () -> List.of(rpEncKey);
    }

    @Override
    public KeyStore relyingPartySigJwksKeystore(URI issuer) {
      return () -> List.of(rpSigKeysProvider.apply(issuer));
    }
  }
}
