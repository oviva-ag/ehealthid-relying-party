package com.oviva.ehealthid.relyingparty.ws;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oviva.ehealthid.auth.AuthenticationFlow;
import com.oviva.ehealthid.auth.steps.SelectSectoralIdpStep;
import com.oviva.ehealthid.auth.steps.TrustedSectoralIdpStep;
import com.oviva.ehealthid.relyingparty.cfg.RelyingPartyConfig;
import com.oviva.ehealthid.relyingparty.svc.SessionRepo;
import com.oviva.ehealthid.relyingparty.svc.SessionRepo.Session;
import com.oviva.ehealthid.relyingparty.svc.TokenIssuer;
import com.oviva.ehealthid.relyingparty.svc.TokenIssuer.Code;
import com.oviva.ehealthid.relyingparty.svc.TokenIssuer.Token;
import com.oviva.ehealthid.relyingparty.util.IdGenerator;
import com.oviva.ehealthid.relyingparty.ws.AuthEndpoint.TokenResponse;
import jakarta.ws.rs.core.Response.Status;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class AuthEndpointTest {

  private static final URI BASE_URI = URI.create("https://idp.example.com");
  private static final URI REDIRECT_URI = URI.create("https://myapp.example.com");

  @Test
  void auth_badScopes() {

    var rpConfig = new RelyingPartyConfig(null, List.of(REDIRECT_URI));

    var sut = new AuthEndpoint(BASE_URI, rpConfig, null, null, null);

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
    var config = new RelyingPartyConfig(null, List.of(REDIRECT_URI));

    var sut = new AuthEndpoint(BASE_URI, config, null, null, null);

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
    var config = new RelyingPartyConfig(null, List.of(REDIRECT_URI));

    var sut = new AuthEndpoint(BASE_URI, config, null, null, null);

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
    var config = new RelyingPartyConfig(List.of("code"), List.of(REDIRECT_URI));

    var sessionRepo = mock(SessionRepo.class);
    var sut = new AuthEndpoint(BASE_URI, config, sessionRepo, null, null);

    var scope = "openid";
    var state = UUID.randomUUID().toString();
    var nonce = UUID.randomUUID().toString();
    var responseType = "badtype";
    var clientId = "myapp";

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
    var config = new RelyingPartyConfig(List.of("code"), List.of(REDIRECT_URI));

    var idpRedirectUrl = URI.create("https://federated-idp.example.com");

    var trustedIdpStep = mock(TrustedSectoralIdpStep.class);
    when(trustedIdpStep.idpRedirectUri()).thenReturn(idpRedirectUrl);

    var selectIdpStep = mock(SelectSectoralIdpStep.class);
    when(selectIdpStep.fetchIdpOptions()).thenReturn(List.of());
    when(selectIdpStep.redirectToSectoralIdp(any())).thenReturn(trustedIdpStep);

    var authFlow = mock(AuthenticationFlow.class);
    when(authFlow.start(any())).thenReturn(selectIdpStep);

    var sessionRepo = mock(SessionRepo.class);
    var sut = new AuthEndpoint(BASE_URI, config, sessionRepo, null, authFlow);

    var scope = "openid";
    var state = UUID.randomUUID().toString();
    var nonce = UUID.randomUUID().toString();
    var responseType = "code";
    var clientId = "myapp";

    // when
    try (var res = sut.auth(scope, state, responseType, clientId, REDIRECT_URI.toString(), nonce)) {

      // then

      // TODO? Check form?
      assertEquals(Status.OK.getStatusCode(), res.getStatus());

      var sessionCookie = res.getCookies().get("session_id");
      assertNotNull(sessionCookie.getValue());
    }
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {" ", "  \n\t"})
  void callback_noSessionId(String sessionId) {

    var config = new RelyingPartyConfig(null, null);

    var sut = new AuthEndpoint(BASE_URI, config, null, null, null);

    // when
    try (var res = sut.callback(sessionId, "")) {

      // then
      assertEquals(Status.BAD_REQUEST.getStatusCode(), res.getStatus());
    }
  }

  @Test
  void callback_unknownSession() {

    var config = new RelyingPartyConfig(null, null);

    var sessionRepo = mock(SessionRepo.class);

    var sut = new AuthEndpoint(BASE_URI, config, sessionRepo, null, null);

    var sessionId = UUID.randomUUID().toString();

    when(sessionRepo.load(sessionId)).thenReturn(null);

    // when
    try (var res = sut.callback(sessionId, null)) {

      // then
      assertEquals(Status.BAD_REQUEST.getStatusCode(), res.getStatus());
    }
  }

  @Test
  void callback() {

    var config = new RelyingPartyConfig(List.of("code"), List.of(REDIRECT_URI));

    var sessionRepo = mock(SessionRepo.class);
    var tokenIssuer = mock(TokenIssuer.class);

    var sut = new AuthEndpoint(BASE_URI, config, sessionRepo, tokenIssuer, null);

    var sessionId = UUID.randomUUID().toString();

    var state = "mySuperDuperState";
    var nonce = "20e5ed8b-f96b-48de-ae73-4460bcfc35a1";
    var clientId = "myapp";

    var trustedIdpStep = mock(TrustedSectoralIdpStep.class);

    var session =
        Session.create()
            .id(sessionId)
            .state(state)
            .nonce(nonce)
            .redirectUri(REDIRECT_URI)
            .clientId(clientId)
            .trustedSectoralIdpStep(trustedIdpStep)
            .build();

    when(sessionRepo.load(sessionId)).thenReturn(session);

    var code = "6238e4504332468aa0c12e300787fded";

    when(trustedIdpStep.exchangeSectoralIdpCode(code, null)).thenReturn(null);

    var issued = new Code(code, null, null, REDIRECT_URI, nonce, clientId, null);
    when(tokenIssuer.issueCode(session, null)).thenReturn(issued);

    // when
    try (var res = sut.callback(sessionId, null)) {

      // then
      assertEquals(
          "https://myapp.example.com?code=6238e4504332468aa0c12e300787fded&state=mySuperDuperState",
          res.getHeaderString("location"));
    }
  }

  @Test
  void token_badGrantType() {

    var config = new RelyingPartyConfig(List.of("code"), List.of(REDIRECT_URI));

    var tokenIssuer = mock(TokenIssuer.class);

    var sut = new AuthEndpoint(BASE_URI, config, null, tokenIssuer, null);

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

    var config = new RelyingPartyConfig(List.of("code"), List.of(REDIRECT_URI));

    var tokenIssuer = mock(TokenIssuer.class);

    var sut = new AuthEndpoint(BASE_URI, config, null, tokenIssuer, null);

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

    var config = new RelyingPartyConfig(List.of("code"), List.of(REDIRECT_URI));

    var tokenIssuer = mock(TokenIssuer.class);

    var sut = new AuthEndpoint(BASE_URI, config, null, tokenIssuer, null);

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

  @Test
  void selectIdp() {

    var sessionRepo = mock(SessionRepo.class);

    var sessionId = IdGenerator.generateID();
    var selectIdpStep = mock(SelectSectoralIdpStep.class);

    var selectedIdpIssuer = "https://aok-testfalen.example.com";

    var idpRedirect = URI.create(selectedIdpIssuer).resolve("/auth/login");

    var trustedIdpStep = mock(TrustedSectoralIdpStep.class);
    when(selectIdpStep.redirectToSectoralIdp(selectedIdpIssuer)).thenReturn(trustedIdpStep);
    when(trustedIdpStep.idpRedirectUri()).thenReturn(idpRedirect);

    var session = Session.create().id(sessionId).selectSectoralIdpStep(selectIdpStep).build();
    when(sessionRepo.load(sessionId)).thenReturn(session);

    var sut = new AuthEndpoint(BASE_URI, null, sessionRepo, null, null);

    // when
    var res = sut.postSelectIdp(sessionId, selectedIdpIssuer);

    // then
    assertEquals(idpRedirect, res.getLocation());

    var captor = ArgumentCaptor.forClass(Session.class);
    verify(sessionRepo).save(captor.capture());

    var savedSession = captor.getValue();
    assertEquals(trustedIdpStep, savedSession.trustedSectoralIdpStep());
  }

  @Test
  void selectIdp_noSession() {
    var sessionRepo = mock(SessionRepo.class);

    var sessionId = IdGenerator.generateID();
    var selectedIdpIssuer = "https://aok-testfalen.example.com";

    when(sessionRepo.load(sessionId)).thenReturn(null);

    var sut = new AuthEndpoint(BASE_URI, null, sessionRepo, null, null);

    // when
    var res = sut.postSelectIdp(sessionId, selectedIdpIssuer);

    // then
    verify(sessionRepo).load(sessionId);
    assertEquals(Status.BAD_REQUEST.getStatusCode(), res.getStatus());
  }

  @Test
  void selectIdp_nothingSelected() {

    var sessionId = "1234";
    var sut = new AuthEndpoint(BASE_URI, null, null, null, null);

    // when
    var res = sut.postSelectIdp(sessionId, null);

    // then
    assertEquals(Status.BAD_REQUEST.getStatusCode(), res.getStatus());
  }
}
