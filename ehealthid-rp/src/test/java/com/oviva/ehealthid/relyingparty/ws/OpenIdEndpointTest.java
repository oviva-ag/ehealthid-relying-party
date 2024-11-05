package com.oviva.ehealthid.relyingparty.ws;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.oviva.ehealthid.relyingparty.cfg.RelyingPartyConfig;
import java.net.URI;
import java.text.ParseException;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class OpenIdEndpointTest {

  private static final URI BASE_URI = URI.create("https://idp.example.com");

  @Test
  void openIdConfiguration() {

    var config = new RelyingPartyConfig(null, null);
    var sut = new OpenIdEndpoint(BASE_URI, config, null);

    // when
    OpenIdConfiguration body;
    try (var res = sut.openIdConfiguration()) {
      body = res.readEntity(OpenIdConfiguration.class);
    }

    // then
    assertEquals(BASE_URI.toString(), body.issuer());
    assertEquals(BASE_URI.resolve("/auth").toString(), body.authorizationEndpoint());
    assertEquals(BASE_URI.resolve("/jwks.json").toString(), body.jwksUri());
    assertEquals(BASE_URI.resolve("/auth/token").toString(), body.tokenEndpoint());
    assertEquals(List.of("ES256"), body.idTokenSigningAlgValuesSupported());
  }

  @Test
  void jwks() throws ParseException {

    var key =
        ECKey.parse(
            """
      {"kty":"EC","use":"sig","crv":"P-256","x":"yi3EF1QZS1EiAfAAfjoDyZkRnf59H49gUyklmfwKwSY","y":"Y_SGRGjwacDuT8kbcaX1Igyq8aRfJFNBMKLb2yr0x18"}
      """);
    var keyStore = mock(Supplier.class);
    when(keyStore.get()).thenReturn(key);

    var sut = new OpenIdEndpoint(BASE_URI, null, keyStore);

    try (var res = sut.jwks()) {
      var jwks = res.readEntity(JWKSet.class);
      assertEquals(key, jwks.getKeys().get(0));
    }
  }
}
