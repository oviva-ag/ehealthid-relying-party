package com.oviva.gesundheitsid.relyingparty.ws;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.oviva.gesundheitsid.relyingparty.cfg.Config;
import com.oviva.gesundheitsid.relyingparty.svc.KeyStore;
import com.oviva.gesundheitsid.relyingparty.svc.SessionRepo;
import com.oviva.gesundheitsid.relyingparty.svc.TokenIssuer;
import com.oviva.gesundheitsid.relyingparty.svc.TokenIssuer.Code;
import com.oviva.gesundheitsid.relyingparty.svc.TokenIssuer.Token;
import com.oviva.gesundheitsid.relyingparty.ws.OpenIdEndpoint.TokenResponse;
import jakarta.ws.rs.core.Response.Status;
import java.net.URI;
import java.text.ParseException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class OpenIdEndpointTest {

  private static final URI BASE_URI = URI.create("https://idp.example.com");
  private static final URI REDIRECT_URI = URI.create("https://myapp.example.com");

  @Test
  void openIdConfiguration() {

    var config = new Config(0, BASE_URI, null, null);
    var sut = new OpenIdEndpoint(config, null, null, null);

    // when
    OpenIdConfiguration body;
    try (var res = sut.openIdConfiguration()) {
      body = res.readEntity(OpenIdConfiguration.class);
    }

    // then
    assertEquals(BASE_URI.toString(), body.issuer());
    assertEquals(BASE_URI.resolve("/auth").toString(), body.authorizationEndpoint());
    assertEquals(BASE_URI.resolve("/jwks.json").toString(), body.jwksUri());
    assertEquals(BASE_URI.resolve("/token").toString(), body.tokenEndpoint());
    assertEquals(List.of("ES256"), body.idTokenSigningAlgValuesSupported());
  }

  @Test
  void auth_badScopes() {
    var config = new Config(0, BASE_URI, null, List.of(REDIRECT_URI));

    var sut = new OpenIdEndpoint(config, null, null, null);

    var scope = "openid email";
    var state = UUID.randomUUID().toString();
    var nonce = UUID.randomUUID().toString();
    var responseType = "code";
    var clientId = "myapp";

    // when
    try (var res = sut.auth(scope, state, responseType, clientId, REDIRECT_URI.toString(), nonce)) {
      // then
      assertEquals(Status.SEE_OTHER.getStatusCode(), res.getStatus());
      var location = res.getHeaderString("location");
      assertEquals(
          "https://myapp.example.com?error=invalid_scope&error_description=scope+%%27openid+email%%27+not+supported&state=%s"
              .formatted(state),
          location);
    }
  }

  @Test
  void auth_malformedRedirect() {
    var config = new Config(0, BASE_URI, null, List.of(REDIRECT_URI));

    var sut = new OpenIdEndpoint(config, null, null, null);

    var scope = "openid email";
    var state = UUID.randomUUID().toString();
    var nonce = UUID.randomUUID().toString();
    var responseType = "code";
    var clientId = "myapp";
    var redirectUri = "httpyolo://yolo!";

    // when
    try (var res = sut.auth(scope, state, responseType, clientId, redirectUri, nonce)) {
      // then
      assertEquals(Status.BAD_REQUEST.getStatusCode(), res.getStatus());
    }
  }

  @Test
  void auth_untrustedRedirect() {
    var config = new Config(0, BASE_URI, null, List.of(REDIRECT_URI));

    var sut = new OpenIdEndpoint(config, null, null, null);

    var scope = "openid email";
    var state = UUID.randomUUID().toString();
    var nonce = UUID.randomUUID().toString();
    var responseType = "code";
    var clientId = "myapp";
    var redirectUri = "https://bad.example.com/evil";

    // when
    try (var res = sut.auth(scope, state, responseType, clientId, redirectUri, nonce)) {
      // then
      assertEquals(Status.BAD_REQUEST.getStatusCode(), res.getStatus());

      // according to spec we _MUST NOT_ redirect if we don't trust the redirect
      assertNull(res.getHeaderString("location"));
    }
  }

  @Test
  void auth_badResponseType() {
    var config = new Config(0, BASE_URI, List.of("code"), List.of(REDIRECT_URI));

    var sessionRepo = mock(SessionRepo.class);
    var sut = new OpenIdEndpoint(config, sessionRepo, null, null);

    var scope = "openid";
    var state = UUID.randomUUID().toString();
    var nonce = UUID.randomUUID().toString();
    var responseType = "badtype";
    var clientId = "myapp";

    var sessionId = UUID.randomUUID().toString();
    when(sessionRepo.save(any())).thenReturn(sessionId);

    // when
    try (var res = sut.auth(scope, state, responseType, clientId, REDIRECT_URI.toString(), nonce)) {

      // then
      assertEquals(Status.SEE_OTHER.getStatusCode(), res.getStatus());
      var location = res.getHeaderString("location");
      assertEquals(
          "https://myapp.example.com?error=unsupported_response_type&error_description=unsupported+response+type%3A+%27badtype%27&state="
              + state,
          location);
    }
  }

  @Test
  void auth_success() {
    var config = new Config(0, BASE_URI, List.of("code"), List.of(REDIRECT_URI));

    var sessionRepo = mock(SessionRepo.class);
    var sut = new OpenIdEndpoint(config, sessionRepo, null, null);

    var scope = "openid";
    var state = UUID.randomUUID().toString();
    var nonce = UUID.randomUUID().toString();
    var responseType = "code";
    var clientId = "myapp";

    var sessionId = UUID.randomUUID().toString();
    when(sessionRepo.save(any())).thenReturn(sessionId);

    // when
    try (var res = sut.auth(scope, state, responseType, clientId, REDIRECT_URI.toString(), nonce)) {

      // then
      assertEquals(Status.OK.getStatusCode(), res.getStatus());
      var sessionCookie = res.getCookies().get("session_id");
      assertEquals(sessionId, sessionCookie.getValue());
    }
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {" ", "  \n\t"})
  void callback_noSessionId(String sessionId) {

    var config = new Config(0, null, null, null);

    var sut = new OpenIdEndpoint(config, null, null, null);

    // when
    try (var res = sut.callback(sessionId)) {

      // then
      assertEquals(Status.BAD_REQUEST.getStatusCode(), res.getStatus());
    }
  }

  @Test
  void callback_unknownSession() {

    var config = new Config(0, null, null, null);

    var sessionRepo = mock(SessionRepo.class);

    var sut = new OpenIdEndpoint(config, sessionRepo, null, null);

    var sessionId = UUID.randomUUID().toString();

    when(sessionRepo.load(sessionId)).thenReturn(null);

    // when
    try (var res = sut.callback(sessionId)) {

      // then
      assertEquals(Status.BAD_REQUEST.getStatusCode(), res.getStatus());
    }
  }

  @Test
  void callback() {

    var config = new Config(0, BASE_URI, List.of("code"), List.of(REDIRECT_URI));

    var sessionRepo = mock(SessionRepo.class);
    var tokenIssuer = mock(TokenIssuer.class);

    var sut = new OpenIdEndpoint(config, sessionRepo, tokenIssuer, null);

    var sessionId = UUID.randomUUID().toString();

    var state = "mySuperDuperState";
    var nonce = "20e5ed8b-f96b-48de-ae73-4460bcfc35a1";
    var clientId = "myapp";

    var session = new SessionRepo.Session(sessionId, state, nonce, REDIRECT_URI, clientId);
    when(sessionRepo.load(sessionId)).thenReturn(session);

    var code = "6238e4504332468aa0c12e300787fded";
    var issued = new Code(code, null, null, REDIRECT_URI, nonce, clientId);
    when(tokenIssuer.issueCode(session)).thenReturn(issued);

    // when
    try (var res = sut.callback(sessionId)) {

      // then
      assertEquals(
          "https://myapp.example.com?code=6238e4504332468aa0c12e300787fded&state=mySuperDuperState",
          res.getHeaderString("location"));
    }
  }

  @Test
  void jwks() throws ParseException {

    var key =
        ECKey.parse(
            """
      {"kty":"EC","use":"sig","crv":"P-256","x":"yi3EF1QZS1EiAfAAfjoDyZkRnf59H49gUyklmfwKwSY","y":"Y_SGRGjwacDuT8kbcaX1Igyq8aRfJFNBMKLb2yr0x18"}
      """);
    var keyStore = mock(KeyStore.class);
    when(keyStore.signingKey()).thenReturn(key);

    var sut = new OpenIdEndpoint(null, null, null, keyStore);

    try (var res = sut.jwks()) {
      var jwks = res.readEntity(JWKSet.class);
      assertEquals(key, jwks.getKeys().get(0));
    }
  }

  @Test
  void token_badGrantType() {

    var config = new Config(0, BASE_URI, List.of("code"), List.of(REDIRECT_URI));

    var tokenIssuer = mock(TokenIssuer.class);

    var sut = new OpenIdEndpoint(config, null, tokenIssuer, null);

    var clientId = "myapp";

    var grantType = "yolo";

    var code = "6238e4504332468aa0c12e300787fded";

    when(tokenIssuer.redeem(code, null, null)).thenReturn(null);

    // when
    try (var res = sut.token(code, grantType, REDIRECT_URI.toString(), clientId)) {

      // then
      assertEquals(Status.BAD_REQUEST.getStatusCode(), res.getStatus());
    }
  }

  @Test
  void token_badCode() {

    var config = new Config(0, BASE_URI, List.of("code"), List.of(REDIRECT_URI));

    var tokenIssuer = mock(TokenIssuer.class);

    var sut = new OpenIdEndpoint(config, null, tokenIssuer, null);

    var clientId = "myapp";

    var grantType = "authorization_code";

    var code = "6238e4504332468aa0c12e300787fded";

    when(tokenIssuer.redeem(code, REDIRECT_URI.toString(), clientId)).thenReturn(null);

    // when
    try (var res = sut.token(code, grantType, REDIRECT_URI.toString(), clientId)) {

      // then
      assertEquals(Status.BAD_REQUEST.getStatusCode(), res.getStatus());
    }
  }

  @Test
  void token() {

    var config = new Config(0, BASE_URI, List.of("code"), List.of(REDIRECT_URI));

    var tokenIssuer = mock(TokenIssuer.class);

    var sut = new OpenIdEndpoint(config, null, tokenIssuer, null);

    var clientId = "myapp";

    var grantType = "authorization_code";

    var idToken = UUID.randomUUID().toString();
    var accessToken = UUID.randomUUID().toString();
    var expiresIn = 3600;

    var code = "6238e4504332468aa0c12e300787fded";
    var token = new Token(accessToken, idToken, expiresIn);
    when(tokenIssuer.redeem(code, REDIRECT_URI.toString(), clientId)).thenReturn(token);

    // when
    try (var res = sut.token(code, grantType, REDIRECT_URI.toString(), clientId)) {

      // then
      assertEquals(Status.OK.getStatusCode(), res.getStatus());
      var got = res.readEntity(TokenResponse.class);

      assertEquals(idToken, got.idToken());
      assertEquals(accessToken, got.accessToken());
      assertEquals(expiresIn, got.expiresIn());
    }
  }
}
