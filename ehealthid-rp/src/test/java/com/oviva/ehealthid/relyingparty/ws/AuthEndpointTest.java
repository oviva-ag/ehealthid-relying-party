package com.oviva.ehealthid.relyingparty.ws;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oviva.ehealthid.fedclient.IdpEntry;
import com.oviva.ehealthid.relyingparty.svc.AuthService;
import com.oviva.ehealthid.relyingparty.svc.AuthService.AuthorizationRequest;
import com.oviva.ehealthid.relyingparty.svc.AuthService.AuthorizationResponse;
import com.oviva.ehealthid.relyingparty.svc.AuthService.CallbackRequest;
import com.oviva.ehealthid.relyingparty.svc.AuthService.SelectedIdpRequest;
import com.oviva.ehealthid.relyingparty.svc.ValidationException;
import com.oviva.ehealthid.relyingparty.util.IdGenerator;
import com.oviva.ehealthid.relyingparty.ws.AuthEndpoint.AuthResponse;
import jakarta.ws.rs.core.Response.Status;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuthEndpointTest {

  private static final String REDIRECT_URI = "https://myapp.example.com";

  @Test
  void auth_badRequest() {

    var authService = mock(AuthService.class);
    doThrow(ValidationException.class).when(authService).auth(any());
    var sut = new AuthEndpoint(authService);

    var scope = "openid";
    var state = UUID.randomUUID().toString();
    var nonce = UUID.randomUUID().toString();
    var responseType = "badtype";
    var clientId = "myapp";

    // when & then
    assertThrows(
        ValidationException.class,
        () -> sut.auth(scope, state, responseType, clientId, REDIRECT_URI, nonce));
  }

  @Test
  void auth_success_passParams() {

    var sessionId = IdGenerator.generateID();
    var authService = mock(AuthService.class);
    when(authService.auth(any())).thenReturn(new AuthorizationResponse(List.of(), sessionId));
    var sut = new AuthEndpoint(authService);

    var scope = "openid";
    var state = UUID.randomUUID().toString();
    var nonce = UUID.randomUUID().toString();
    var responseType = "code";
    var clientId = "myapp";

    // when
    try (var res = sut.auth(scope, state, responseType, clientId, REDIRECT_URI, nonce)) {

      // then
      var captor = ArgumentCaptor.forClass(AuthorizationRequest.class);
      verify(authService).auth(captor.capture());

      var req = captor.getValue();
      assertEquals(REDIRECT_URI, req.redirectUri().toString());
      assertEquals(scope, req.scope());
      assertEquals(state, req.state());
      assertEquals(responseType, req.responseType());
      assertEquals(clientId, req.clientId());
      assertEquals(nonce, req.nonce());
    }
  }

  @Test
  void auth_success() {

    var sessionId = IdGenerator.generateID();
    var authService = mock(AuthService.class);
    when(authService.auth(any())).thenReturn(new AuthorizationResponse(List.of(), sessionId));
    var sut = new AuthEndpoint(authService);

    var scope = "openid";
    var state = UUID.randomUUID().toString();
    var nonce = UUID.randomUUID().toString();
    var responseType = "code";
    var clientId = "myapp";

    // when
    try (var res = sut.auth(scope, state, responseType, clientId, REDIRECT_URI, nonce)) {

      // then
      assertEquals(Status.OK.getStatusCode(), res.getStatus());

      var sessionCookie = res.getCookies().get("session_id");
      assertEquals(sessionId, sessionCookie.getValue());
    }
  }

  @Test
  void authJson_success() {
    var identityProviders = List.of(new IdpEntry("a", "A", null), new IdpEntry("b", "B", null));

    var sessionId = IdGenerator.generateID();
    var authService = mock(AuthService.class);
    when(authService.auth(any()))
        .thenReturn(new AuthorizationResponse(identityProviders, sessionId));
    var sut = new AuthEndpoint(authService);

    var scope = "openid";
    var state = UUID.randomUUID().toString();
    var nonce = UUID.randomUUID().toString();
    var responseType = "code";
    var clientId = "myapp";

    // when
    try (var res = sut.authJson(scope, state, responseType, clientId, REDIRECT_URI, nonce)) {

      // then
      assertEquals(Status.OK.getStatusCode(), res.getStatus());

      var authResponse = res.readEntity(AuthResponse.class);
      var actualIdentityProviders = authResponse.identityProviders();
      assertEquals(identityProviders.size(), actualIdentityProviders.size());
      for (int i = 0; i < identityProviders.size(); i++) {
        var expected = identityProviders.get(i);
        var actual = actualIdentityProviders.get(i);
        assertEquals(expected.iss(), actual.iss());
        assertEquals(expected.name(), actual.name());
        assertEquals(expected.logoUrl(), actual.logoUrl());
      }

      var sessionCookie = res.getCookies().get("session_id");
      assertEquals(sessionId, sessionCookie.getValue());
    }
  }

  @Test
  void callback_success() {

    var callbackRedirect = URI.create("https://app.example.com/success");
    var authService = mock(AuthService.class);
    when(authService.callback(any())).thenReturn(callbackRedirect);
    var sut = new AuthEndpoint(authService);

    // when
    try (var res = sut.callback(null, null)) {

      // then
      assertEquals(Status.SEE_OTHER.getStatusCode(), res.getStatus());
      var redirect = res.getLocation().toString();
      assertEquals(callbackRedirect.toString(), redirect);
    }
  }

  @Test
  void callback_success_passParams() {

    var callbackRedirect = URI.create("https://app.example.com/success");
    var authService = mock(AuthService.class);
    when(authService.callback(any())).thenReturn(callbackRedirect);
    var sut = new AuthEndpoint(authService);

    var code = "myCode";
    var sessionId = IdGenerator.generateID();

    // when
    try (var res = sut.callback(sessionId, code)) {

      var captor = ArgumentCaptor.forClass(CallbackRequest.class);
      verify(authService).callback(captor.capture());

      var req = captor.getValue();
      assertEquals(code, req.code());
      assertEquals(sessionId, req.sessionId());
    }
  }

  @Test
  void selectIdp() {

    var sessionId = IdGenerator.generateID();

    var selectedIdpIssuer = "https://aok-testfalen.example.com";

    var idpRedirect = URI.create(selectedIdpIssuer).resolve("/auth/login");

    var authService = mock(AuthService.class);
    when(authService.selectedIdentityProvider(any())).thenReturn(idpRedirect);
    var sut = new AuthEndpoint(authService);

    // when
    try (var res = sut.postSelectIdp(sessionId, selectedIdpIssuer)) {
      // then
      assertEquals(idpRedirect, res.getLocation());
    }
  }

  @Test
  void selectIdp_passParams() {

    var sessionId = IdGenerator.generateID();

    var selectedIdpIssuer = "https://aok-testfalen.example.com";

    var idpRedirect = URI.create(selectedIdpIssuer).resolve("/auth/login");

    var authService = mock(AuthService.class);
    when(authService.selectedIdentityProvider(any())).thenReturn(idpRedirect);
    var sut = new AuthEndpoint(authService);

    // when
    try (var res = sut.postSelectIdp(sessionId, selectedIdpIssuer)) {
      // then
      var captor = ArgumentCaptor.forClass(SelectedIdpRequest.class);
      verify(authService).selectedIdentityProvider(captor.capture());

      var req = captor.getValue();
      assertEquals(selectedIdpIssuer, req.selectedIdentityProvider());
      assertEquals(sessionId, req.sessionId());
    }
  }
}
