package com.oviva.ehealthid.relyingparty.svc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oviva.ehealthid.auth.AuthenticationFlow;
import com.oviva.ehealthid.auth.steps.SelectSectoralIdpStep;
import com.oviva.ehealthid.auth.steps.TrustedSectoralIdpStep;
import com.oviva.ehealthid.relyingparty.cfg.RelyingPartyConfig;
import com.oviva.ehealthid.relyingparty.fed.FederationConfig;
import com.oviva.ehealthid.relyingparty.svc.AuthService.AuthorizationRequest;
import com.oviva.ehealthid.relyingparty.svc.AuthService.CallbackRequest;
import com.oviva.ehealthid.relyingparty.svc.AuthService.SelectedIdpRequest;
import com.oviva.ehealthid.relyingparty.svc.SessionRepo.Session;
import com.oviva.ehealthid.relyingparty.svc.TokenIssuer.Code;
import com.oviva.ehealthid.relyingparty.util.IdGenerator;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class AuthServiceTest {

  private static final URI BASE_URI = URI.create("https://idp.example.com");
  private static final URI REDIRECT_URI = URI.create("https://myapp.example.com");

  @Test
  void auth_badScopes() {

    var rpConfig = new RelyingPartyConfig(null, List.of(REDIRECT_URI));

    var sut = new AuthService(BASE_URI, rpConfig, null, null, null, null);

    var scope = "openid email";
    var state = UUID.randomUUID().toString();
    var nonce = UUID.randomUUID().toString();
    var responseType = "code";
    var clientId = "myapp";

    // when
    var req =
        new AuthorizationRequest(scope, state, responseType, clientId, REDIRECT_URI, null, nonce);
    var e = assertThrows(ValidationException.class, () -> sut.auth(req));
    // then
    assertEquals(
        "https://myapp.example.com?error=invalid_scope&error_description=error.unsupportedScope&state=%s"
            .formatted(state),
        e.seeOther().toString());
  }

  @Test
  void auth_untrustedRedirect() {
    var config = new RelyingPartyConfig(null, List.of(REDIRECT_URI));

    var sut = new AuthService(BASE_URI, config, null, null, null, null);

    var scope = "openid email";
    var state = UUID.randomUUID().toString();
    var nonce = UUID.randomUUID().toString();
    var responseType = "code";
    var clientId = "myapp";
    var redirectUri = URI.create("https://bad.example.com/evil");
    var req =
        new AuthorizationRequest(scope, state, responseType, clientId, redirectUri, null, nonce);

    // when & then
    assertThrows(ValidationException.class, () -> sut.auth(req));
  }

  @Test
  void auth_badResponseType() {
    var config = new RelyingPartyConfig(List.of("code"), List.of(REDIRECT_URI));

    var sessionRepo = mock(SessionRepo.class);
    var sut = new AuthService(BASE_URI, config, null, sessionRepo, null, null);

    var scope = "openid";
    var state = UUID.randomUUID().toString();
    var nonce = UUID.randomUUID().toString();
    var responseType = "badtype";
    var clientId = "myapp";
    var req =
        new AuthorizationRequest(scope, state, responseType, clientId, REDIRECT_URI, null, nonce);

    var e = assertThrows(ValidationException.class, () -> sut.auth(req));

    // when
    assertEquals(
        "https://myapp.example.com?error=unsupported_response_type&error_description=error.unsupportedResponseType&state="
            + state,
        e.seeOther().toString());
  }

  @Test
  void auth_success() {
    var config = new RelyingPartyConfig(List.of("code"), List.of(REDIRECT_URI));
    var fedConfig = FederationConfig.create().scopes(List.of("openid")).build();

    var idpRedirectUrl = URI.create("https://federated-idp.example.com");

    var trustedIdpStep = mock(TrustedSectoralIdpStep.class);
    when(trustedIdpStep.idpRedirectUri()).thenReturn(idpRedirectUrl);

    var selectIdpStep = mock(SelectSectoralIdpStep.class);
    when(selectIdpStep.fetchIdpOptions()).thenReturn(List.of());
    when(selectIdpStep.redirectToSectoralIdp(any())).thenReturn(trustedIdpStep);

    var authFlow = mock(AuthenticationFlow.class);
    when(authFlow.start(any())).thenReturn(selectIdpStep);

    var sessionRepo = mock(SessionRepo.class);
    var sut = new AuthService(BASE_URI, config, fedConfig, sessionRepo, null, authFlow);

    var scope = "openid";
    var state = UUID.randomUUID().toString();
    var nonce = UUID.randomUUID().toString();
    var responseType = "code";
    var clientId = "myapp";
    var req =
        new AuthorizationRequest(scope, state, responseType, clientId, REDIRECT_URI, null, nonce);

    // when
    var res = sut.auth(req);
    assertNotNull(res.sessionId());
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {" ", "  \n\t"})
  void callback_noSessionId(String sessionId) {

    var config = new RelyingPartyConfig(null, null);

    var sut = new AuthService(BASE_URI, config, null, null, null, null);

    var req = new CallbackRequest(sessionId, "");

    // when & then
    assertThrows(ValidationException.class, () -> sut.callback(req));
  }

  @Test
  void callback_unknownSession() {

    var config = new RelyingPartyConfig(null, null);

    var sessionRepo = mock(SessionRepo.class);

    var sut = new AuthService(BASE_URI, config, null, sessionRepo, null, null);

    var sessionId = UUID.randomUUID().toString();

    when(sessionRepo.load(sessionId)).thenReturn(null);

    var req = new CallbackRequest(sessionId, null);

    // when & then
    assertThrows(ValidationException.class, () -> sut.callback(req));
  }

  @Test
  void callback() {

    var config = new RelyingPartyConfig(List.of("code"), List.of(REDIRECT_URI));

    var sessionRepo = mock(SessionRepo.class);
    var tokenIssuer = mock(TokenIssuer.class);

    var sut = new AuthService(BASE_URI, config, null, sessionRepo, tokenIssuer, null);

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
    when(sessionRepo.remove(sessionId)).thenReturn(session);

    var code = "6238e4504332468aa0c12e300787fded";

    when(trustedIdpStep.exchangeSectoralIdpCode(code, null)).thenReturn(null);

    var issued = new Code(code, null, null, REDIRECT_URI, nonce, clientId, null);
    when(tokenIssuer.issueCode(session, null)).thenReturn(issued);

    var req = new CallbackRequest(sessionId, "somecode");

    // when
    var res = sut.callback(req);

    // then
    assertEquals(
        "https://myapp.example.com?code=6238e4504332468aa0c12e300787fded&state=mySuperDuperState",
        res.toString());

    verify(sessionRepo).remove(sessionId);
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

    var sut = new AuthService(BASE_URI, null, null, sessionRepo, null, null);

    var req = new SelectedIdpRequest(sessionId, selectedIdpIssuer);

    // when
    var res = sut.selectedIdentityProvider(req);

    // then
    assertEquals(idpRedirect, res);

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

    var sut = new AuthService(BASE_URI, null, null, sessionRepo, null, null);
    var req = new SelectedIdpRequest(sessionId, selectedIdpIssuer);

    // when
    assertThrows(ValidationException.class, () -> sut.selectedIdentityProvider(req));

    // then
    verify(sessionRepo).load(sessionId);
  }

  @Test
  void selectIdp_nothingSelected() {

    var sessionId = "1234";
    var sut = new AuthService(BASE_URI, null, null, null, null, null);

    // when
    assertThrows(
        ValidationException.class,
        () -> sut.selectedIdentityProvider(new SelectedIdpRequest(sessionId, null)));
  }
}
