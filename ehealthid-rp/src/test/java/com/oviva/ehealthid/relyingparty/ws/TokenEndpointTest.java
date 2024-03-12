package com.oviva.ehealthid.relyingparty.ws;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.oviva.ehealthid.relyingparty.svc.ClientAuthenticator;
import com.oviva.ehealthid.relyingparty.svc.ClientAuthenticator.Client;
import com.oviva.ehealthid.relyingparty.svc.TokenIssuer;
import com.oviva.ehealthid.relyingparty.svc.TokenIssuer.Token;
import com.oviva.ehealthid.relyingparty.ws.TokenEndpoint.TokenResponse;
import jakarta.ws.rs.core.Response.Status;
import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TokenEndpointTest {

  private static final URI BASE_URI = URI.create("https://idp.example.com");
  private static final URI REDIRECT_URI = URI.create("https://myapp.example.com");

  @Test
  void token_badGrantType() {

    var tokenIssuer = mock(TokenIssuer.class);
    var authenticator = mock(ClientAuthenticator.class);

    var sut = new TokenEndpoint(tokenIssuer, authenticator);

    var clientId = "myapp";

    var grantType = "yolo";

    var code = "6238e4504332468aa0c12e300787fded";

    when(tokenIssuer.redeem(code, null, null)).thenReturn(null);

    // when
    try (var res = sut.token(code, grantType, REDIRECT_URI.toString(), clientId, null, null)) {

      // then
      assertEquals(Status.BAD_REQUEST.getStatusCode(), res.getStatus());
    }
  }

  @Test
  void token_badCode() {

    var tokenIssuer = mock(TokenIssuer.class);
    var authenticator = mock(ClientAuthenticator.class);

    var sut = new TokenEndpoint(tokenIssuer, authenticator);

    var clientId = "myapp";

    var grantType = "authorization_code";

    var code = "6238e4504332468aa0c12e300787fded";

    when(tokenIssuer.redeem(code, REDIRECT_URI.toString(), clientId)).thenReturn(null);
    when(authenticator.authenticate(any())).thenReturn(new Client(clientId));

    // when
    try (var res = sut.token(code, grantType, REDIRECT_URI.toString(), clientId, null, null)) {

      // then
      assertEquals(Status.BAD_REQUEST.getStatusCode(), res.getStatus());
    }
  }

  @Test
  void token() {

    var tokenIssuer = mock(TokenIssuer.class);
    var authenticator = mock(ClientAuthenticator.class);

    var sut = new TokenEndpoint(tokenIssuer, authenticator);

    var clientId = "myapp";

    var grantType = "authorization_code";

    var idToken = UUID.randomUUID().toString();
    var accessToken = UUID.randomUUID().toString();
    var expiresIn = 3600;

    var code = "6238e4504332468aa0c12e300787fded";
    var token = new Token(accessToken, idToken, expiresIn);
    when(tokenIssuer.redeem(code, REDIRECT_URI.toString(), clientId)).thenReturn(token);
    when(authenticator.authenticate(any())).thenReturn(new Client(clientId));

    // when
    try (var res = sut.token(code, grantType, REDIRECT_URI.toString(), clientId, null, null)) {

      // then
      assertEquals(Status.OK.getStatusCode(), res.getStatus());
      var got = res.readEntity(TokenResponse.class);

      assertEquals(idToken, got.idToken());
      assertEquals(accessToken, got.accessToken());
      assertEquals(expiresIn, got.expiresIn());
    }
  }
}
