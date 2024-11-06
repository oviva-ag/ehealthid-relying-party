package com.oviva.ehealthid.relyingparty.providers;

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.oviva.ehealthid.relyingparty.cfg.ConfigProvider;
import com.oviva.ehealthid.relyingparty.test.PropertiesUtils;
import com.oviva.ehealthid.relyingparty.util.KeyGenerator;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BasicKeystoreProviderTest {

  @Test
  void getters() {

    var fedKey = KeyGenerator.generateSigningKey();

    var sub = URI.create("https://myidp.example.com");
    var rpSigKey = KeyGenerator.generateSigningKey();
    var provider = Map.of(sub, rpSigKey);

    var rpEncKey = KeyGenerator.generateSigningKey();

    var opSigKey = KeyGenerator.generateSigningKey();

    var sut =
        new BasicKeystoreProvider.StaticKeyStores(
            provider::get, rpEncKey, opSigKey, List.of(fedKey));

    assertEquals(fedKey, sut.federationSigJwksKeystore().keys().get(0));
    assertEquals(rpSigKey, sut.relyingPartySigJwksKeystore(sub).keys().get(0));
    assertEquals(rpEncKey, sut.relyingPartyEncJwksKeystore().keys().get(0));
    assertEquals(opSigKey, sut.openIdProviderJwksKeystore().keys().get(0));
  }

  @Test
  void fromKeys() {

    var fedKey = KeyGenerator.generateSigningKey();

    var sub = URI.create("https://myidp.example.com");
    var rpSigKey = KeyGenerator.generateSigningKey();
    rpSigKey = KeyGenerator.addCertificate(rpSigKey, sub);

    var rpEncKey = KeyGenerator.generateSigningKey();

    var opSigKey = KeyGenerator.generateSigningKey();

    var sut = BasicKeystoreProvider.fromKeys(new JWKSet(fedKey), rpSigKey, rpEncKey, opSigKey);

    assertEquals(
        rpSigKey.getKeyID(), sut.relyingPartySigJwksKeystore(sub).keys().get(0).getKeyID());

    assertEquals(fedKey, sut.federationSigJwksKeystore().keys().get(0));
    assertEquals(rpEncKey, sut.relyingPartyEncJwksKeystore().keys().get(0));
    assertEquals(opSigKey, sut.openIdProviderJwksKeystore().keys().get(0));
  }

  @Test
  void cachedSigKeys() {

    var fedKey = KeyGenerator.generateSigningKey();

    var sub = URI.create("https://myidp.example.com");
    var rpSigKey = KeyGenerator.generateSigningKey();
    rpSigKey = KeyGenerator.addCertificate(rpSigKey, sub);

    var rpEncKey = KeyGenerator.generateSigningKey();

    var opSigKey = KeyGenerator.generateSigningKey();

    var sut = BasicKeystoreProvider.fromKeys(new JWKSet(fedKey), rpSigKey, rpEncKey, opSigKey);

    var firstInvocation = sut.relyingPartySigJwksKeystore(sub).keys();
    var secondInvocation = sut.relyingPartySigJwksKeystore(sub).keys();
    var thirdInvocation = sut.relyingPartySigJwksKeystore(sub).keys();

    assertEquals(firstInvocation, secondInvocation);
    assertEquals(firstInvocation, thirdInvocation);
  }

  @Test
  void loadFromConfig(@TempDir Path tempDir) throws IOException {

    var k = new JWKSet(KeyGenerator.generateSigningKey());
    var fedKeyName = "fedSigningKey";
    var f = Files.writeString(tempDir.resolve(fedKeyName), k.toString(false));

    var config =
        PropertiesUtils.loadFromString(
            """
                      federation_es_jwks_path=%s
                      openid_rp_sig_jwks_path=%s
                      openid_rp_enc_jwks_path=%s
                      openid_provider_sig_jwks_path=%s
                    """
                .formatted(f, f, f, f));

    ConfigProvider configProvider = c -> Optional.ofNullable(config.get(c));

    // when
    var sut = BasicKeystoreProvider.load(configProvider);

    // then
    var federationSigKeys = sut.federationSigJwksKeystore();
    assertEquals(k.getKeys().get(0), federationSigKeys.keys().get(0));
  }

  @Test
  void loadFromConfig_badKeys(@TempDir Path tempDir) throws IOException, JOSEException {

    var key =
        new RSAKeyGenerator(2048).keyIDFromThumbprint(true).keyUse(KeyUse.SIGNATURE).generate();

    var k = new JWKSet(key);
    var fedKeyName = "fedSigningKey";
    var f = Files.writeString(tempDir.resolve(fedKeyName), k.toString(false));

    var config =
        PropertiesUtils.loadFromString(
            """
                              federation_es_jwks_path=%s
                              openid_rp_sig_jwks_path=%s
                              openid_rp_enc_jwks_path=%s
                              openid_provider_sig_jwks_path=%s
                            """
                .formatted(f, f, f, f));

    ConfigProvider configProvider = c -> Optional.ofNullable(config.get(c));

    // when & then
    assertThrows(IllegalStateException.class, () -> BasicKeystoreProvider.load(configProvider));
  }

  @Test
  void loadFromConfig_noPrivateKey(@TempDir Path tempDir) throws IOException {

    var k = new JWKSet(KeyGenerator.generateSigningKey());
    var fedKeyName = "fedSigningKey";
    var f = Files.writeString(tempDir.resolve(fedKeyName), k.toString(true));

    var config =
        PropertiesUtils.loadFromString(
            """
                              federation_es_jwks_path=%s
                              openid_rp_sig_jwks_path=%s
                              openid_rp_enc_jwks_path=%s
                              openid_provider_sig_jwks_path=%s
                            """
                .formatted(f, f, f, f));

    ConfigProvider configProvider = c -> Optional.ofNullable(config.get(c));

    // when & then
    assertThrows(IllegalStateException.class, () -> BasicKeystoreProvider.load(configProvider));
  }
}
