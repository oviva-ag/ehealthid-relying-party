package com.oviva.ehealthid.relyingparty;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.oviva.ehealthid.relyingparty.cfg.ConfigProvider;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConfigReaderTest {

  @Test
  void read_defaults() {
    var provider = mock(ConfigProvider.class);

    var sut = new ConfigReader(provider);

    var baseUri = "https://rp.example.com";
    var idpDiscoveryUri = "https://sso.example.com/.well-known/openid-configuration";
    var appName = "Awesome DiGA";

    when(provider.get(ConfigReader.CONFIG_FEDERATION_ENC_JWKS_PATH))
        .thenReturn(Optional.of("./src/test/resources/fixtures/example_enc_jwks.json"));
    when(provider.get(ConfigReader.CONFIG_FEDERATION_SIG_JWKS_PATH))
        .thenReturn(Optional.of("./src/test/resources/fixtures/example_sig_jwks.json"));
    when(provider.get(ConfigReader.CONFIG_BASE_URI)).thenReturn(Optional.of(baseUri));
    when(provider.get(ConfigReader.CONFIG_APP_NAME)).thenReturn(Optional.of(appName));
    when(provider.get(ConfigReader.CONFIG_IDP_DISCOVERY_URI))
        .thenReturn(Optional.of(idpDiscoveryUri));

    // when
    var config = sut.read();

    // then
    assertEquals(baseUri, config.baseUri().toString());
    assertEquals(appName, config.federation().appName());

    assertEquals("0.0.0.0", config.host());
    assertEquals(1234, config.port());

    assertEquals(List.of("code"), config.relyingParty().supportedResponseTypes());
    assertEquals(List.of(), config.relyingParty().validRedirectUris());

    assertEquals(baseUri, config.federation().iss().toString());
    assertEquals(baseUri, config.federation().sub().toString());
    assertEquals(
        List.of(
            "openid",
            "urn:telematik:email",
            "urn:telematik:versicherter",
            "urn:telematik:display_name"),
        config.federation().scopes());

    assertNotNull(config.federation().entitySigningKey());
    assertNotNull(config.federation().entitySigningKeys().getKeyByKeyId("test-sig"));
    assertNotNull(config.federation().relyingPartyEncKeys().getKeyByKeyId("test-enc"));
  }

  @Test
  void read_missingJwks() {
    var provider = mock(ConfigProvider.class);

    var sut = new ConfigReader(provider);

    var baseUri = "https://rp.example.com";
    var appName = "Awesome DiGA";

    when(provider.get(ConfigReader.CONFIG_BASE_URI)).thenReturn(Optional.of(baseUri));
    when(provider.get(ConfigReader.CONFIG_APP_NAME)).thenReturn(Optional.of(appName));

    // when
    assertThrows(IllegalArgumentException.class, sut::read);
  }
}
