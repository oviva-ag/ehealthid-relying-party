package com.oviva.ehealthid.relyingparty.ws;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oviva.ehealthid.relyingparty.svc.ClientAuthenticator;
import com.oviva.ehealthid.relyingparty.svc.ClientAuthenticator.Request;
import com.oviva.ehealthid.relyingparty.svc.TokenIssuer;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

@Path("/auth")
public class TokenEndpoint {

  private final TokenIssuer tokenIssuer;

  private final ClientAuthenticator authenticator;

  public TokenEndpoint(TokenIssuer tokenIssuer, ClientAuthenticator authenticator) {
    this.tokenIssuer = tokenIssuer;
    this.authenticator = authenticator;
  }

  // Access Token Request
  // https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.3
  @POST
  @Path("/token")
  @Produces(MediaType.APPLICATION_JSON)
  public Response token(
      @FormParam("code") String code,
      @FormParam("grant_type") String grantType,
      @FormParam("redirect_uri") String redirectUri,
      @FormParam("client_id") String clientId,
      @FormParam("client_assertion_type") String clientAssertionType,
      @FormParam("client_assertion") String clientAssertion) {

    if (!"authorization_code".equals(grantType)) {
      return Response.status(Status.BAD_REQUEST).entity("bad 'grant_type': " + grantType).build();
    }

    var authenticatedClient =
        authenticator.authenticate(new Request(clientId, clientAssertionType, clientAssertion));

    var redeemed = tokenIssuer.redeem(code, redirectUri, authenticatedClient.clientId());
    if (redeemed == null) {
      return Response.status(Status.BAD_REQUEST).entity("invalid code").build();
    }

    var cacheControl = new CacheControl();
    cacheControl.setNoStore(true);

    return Response.ok(
            new TokenResponse(
                redeemed.accessToken(),
                "Bearer",
                null,
                (int) redeemed.expiresInSeconds(),
                redeemed.idToken()))
        .cacheControl(cacheControl)
        .build();
  }

  public record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("token_type") String tokenType,
      @JsonProperty("refresh_token") String refreshToken,
      @JsonProperty("expires_in") int expiresIn,
      @JsonProperty("id_token") String idToken) {}
}
