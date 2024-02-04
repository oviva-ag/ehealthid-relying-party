package com.oviva.gesundheitsid.relyingparty.ws;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nimbusds.jose.jwk.JWKSet;
import com.oviva.gesundheitsid.relyingparty.cfg.Config;
import com.oviva.gesundheitsid.relyingparty.svc.KeyStore;
import com.oviva.gesundheitsid.relyingparty.svc.SessionRepo;
import com.oviva.gesundheitsid.relyingparty.svc.SessionRepo.Session;
import com.oviva.gesundheitsid.relyingparty.svc.TokenIssuer;
import com.oviva.gesundheitsid.relyingparty.ws.OpenIdErrorResponses.ErrorCode;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.NewCookie.SameSite;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriBuilder;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;

@Path("/")
public class OpenIdEndpoint {

  private final Config config;

  private final SessionRepo sessionRepo;
  private final TokenIssuer tokenIssuer;

  private final KeyStore keyStore;

  public OpenIdEndpoint(
      Config config, SessionRepo sessionRepo, TokenIssuer tokenIssuer, KeyStore keyStore) {
    this.config = config;
    this.sessionRepo = sessionRepo;
    this.tokenIssuer = tokenIssuer;
    this.keyStore = keyStore;
  }

  @GET
  @Path("/.well-known/openid-configuration")
  @Produces(MediaType.APPLICATION_JSON)
  public Response openIdConfiguration() {

    var body =
        new OpenIdConfiguration(
            config.baseUri().toString(),
            config.baseUri().resolve("/auth").toString(),
            config.baseUri().resolve("/token").toString(),
            config.baseUri().resolve("/jwks.json").toString(),
            List.of("openid"),
            config.supportedResponseTypes(),
            List.of("authorization_code"),
            List.of("public"),
            List.of("ES256"),
            List.of(),
            List.of());

    return Response.ok(body).build();
  }

  // Authorization Request
  // https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.1
  @GET
  @Path("/auth")
  public Response auth(
      @QueryParam("scope") String scope,
      @QueryParam("state") String state,
      @QueryParam("response_type") String responseType,
      @QueryParam("client_id") String clientId,
      @QueryParam("redirect_uri") String redirectUri,
      @QueryParam("nonce") String nonce) {

    URI parsedRedirect = null;
    try {
      parsedRedirect = new URI(redirectUri);
    } catch (URISyntaxException e) {
      // TODO nice form
      return Response.status(Status.BAD_REQUEST)
          .entity("bad 'redirect_uri': %s".formatted(parsedRedirect))
          .build();
    }

    if (!"https".equals(parsedRedirect.getScheme())) {
      // TODO nice form
      return Response.status(Status.BAD_REQUEST)
          .entity("not https 'redirect_uri': %s".formatted(parsedRedirect))
          .build();
    }

    if (!config.validRedirectUris().contains(parsedRedirect)) {
      // TODO nice form
      return Response.status(Status.BAD_REQUEST)
          .entity("untrusted 'redirect_uri': %s".formatted(parsedRedirect))
          .build();
    }

    if (!"openid".equals(scope)) {
      return OpenIdErrorResponses.redirectWithError(
          parsedRedirect,
          ErrorCode.INVALID_SCOPE,
          state,
          "scope '%s' not supported".formatted(scope));
    }

    if (!config.supportedResponseTypes().contains(responseType)) {
      return OpenIdErrorResponses.redirectWithError(
          parsedRedirect,
          ErrorCode.UNSUPPORTED_RESPONSE_TYPE,
          state,
          "unsupported response type: '%s'".formatted(responseType));
    }

    var session = new Session(null, state, nonce, parsedRedirect, clientId);
    var sessionId = sessionRepo.save(session);

    // TODO: trigger actual flow
    return Response.ok(
            """
      <html>
      <body>
      <form action="/auth/callback" method="POST">
        <div>
          <button>Login 🚀</button>
        </div>
      </form>
      </body>
      </html>
      """,
            MediaType.TEXT_HTML_TYPE)
        .cookie(createSessionCookie(sessionId))
        .build();
  }

  private NewCookie createSessionCookie(String sessionId) {
    return new NewCookie.Builder("session_id")
        .value(sessionId)
        .secure(true)
        .httpOnly(true)
        .sameSite(SameSite.LAX)
        .maxAge(-1) // session scoped
        .path("/auth")
        .build();
  }

  @POST
  @Path("/auth/callback")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response callback(@CookieParam("session_id") String sessionId) {

    if (sessionId == null || sessionId.isBlank()) {
      // TODO: nice UI
      return Response.status(Status.BAD_REQUEST)
          .entity("Session missing!")
          .type(MediaType.TEXT_PLAIN_TYPE)
          .build();
    }

    var session = sessionRepo.load(sessionId);
    if (session == null) {
      // TODO: nice UI
      return Response.status(Status.BAD_REQUEST)
          .entity("Session not found!")
          .type(MediaType.TEXT_PLAIN_TYPE)
          .build();
    }

    // TODO: verify login

    var issued = tokenIssuer.issueCode(session);

    var redirectUri =
        UriBuilder.fromUri(session.redirectUri())
            .queryParam("code", issued.code())
            .queryParam("state", session.state())
            .build();

    return Response.seeOther(redirectUri).build();
  }

  @GET
  @Path("/jwks.json")
  @Produces(MediaType.APPLICATION_JSON)
  public Response jwks() {
    var key = keyStore.signingKey().toPublicJWK();

    var cacheControl = new CacheControl();
    cacheControl.setMaxAge((int) Duration.ofMinutes(30).getSeconds());

    return Response.ok(new JWKSet(List.of(key))).cacheControl(cacheControl).build();
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
      @FormParam("client_id") String clientId) {

    if (!"authorization_code".equals(grantType)) {
      return Response.status(Status.BAD_REQUEST).entity("bad 'grant_type': " + grantType).build();
    }

    var redeemed = tokenIssuer.redeem(code, redirectUri, clientId);
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
