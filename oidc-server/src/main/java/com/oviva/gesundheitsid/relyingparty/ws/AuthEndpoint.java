package com.oviva.gesundheitsid.relyingparty.ws;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oviva.gesundheitsid.auth.AuthenticationFlow;
import com.oviva.gesundheitsid.relyingparty.cfg.RelyingPartyConfig;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

@Path("/auth")
public class AuthEndpoint {

  private final RelyingPartyConfig relyingPartyConfig;

  private final SessionRepo sessionRepo;
  private final TokenIssuer tokenIssuer;

  private final AuthenticationFlow authenticationFlow;

  public AuthEndpoint(
      RelyingPartyConfig relyingPartyConfig,
      SessionRepo sessionRepo,
      TokenIssuer tokenIssuer,
      AuthenticationFlow authenticationFlow) {
    this.relyingPartyConfig = relyingPartyConfig;
    this.sessionRepo = sessionRepo;
    this.tokenIssuer = tokenIssuer;
    this.authenticationFlow = authenticationFlow;
  }

  private static String calculateS256CodeChallenge(String codeVerifier) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      var hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  private static String generateCodeVerifier() {
    var rng = new SecureRandom();

    var bytes = new byte[32];
    rng.nextBytes(bytes);
    return Base64.getUrlEncoder().encodeToString(bytes);
  }

  // Authorization Request
  // https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.1
  @GET
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

    if (!relyingPartyConfig.validRedirectUris().contains(parsedRedirect)) {
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

    if (!relyingPartyConfig.supportedResponseTypes().contains(responseType)) {
      return OpenIdErrorResponses.redirectWithError(
          parsedRedirect,
          ErrorCode.UNSUPPORTED_RESPONSE_TYPE,
          state,
          "unsupported response type: '%s'".formatted(responseType));
    }

    var verifier = generateCodeVerifier(); // for PKCE

    // === federated flow starts

    // those _MUST_ be at most the ones you requested when handing in the entity statement
    var scopes = List.of("openid", "urn:telematik:email", "urn:telematik:versicherter");

    // these should come from the client in the real world
    var codeChallenge = calculateS256CodeChallenge(verifier);

    // ==== 1) start a new flow
    var relyingPartyCallback = relyingPartyConfig.baseUri().resolve("/auth/callback");

    var step1 =
        authenticationFlow.start(
            new AuthenticationFlow.Session(
                "test", "test", relyingPartyCallback, codeChallenge, scopes));

    // ==== 2) get the list of available IDPs
    var idps = step1.fetchIdpOptions();

    // ==== 3) select and IDP

    // for now we hardcode the reference IDP from Gematik
    var sektoralerIdpIss = "https://gsi.dev.gematik.solutions";

    var step2 = step1.redirectToSectoralIdp(sektoralerIdpIss);

    var federatedLogin = step2.idpRedirectUri();

    // store session
    var session = new Session(null, state, nonce, parsedRedirect, clientId, verifier, step2);
    var sessionId = sessionRepo.save(session);

    // TODO: trigger actual flow
    return Response.seeOther(federatedLogin).cookie(createSessionCookie(sessionId)).build();
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

  @GET
  @Path("/callback")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response callback(
      @CookieParam("session_id") String sessionId, @QueryParam("code") String code) {

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

    var idToken =
        session.trustedSectoralIdpStep().exchangeSectoralIdpCode(code, session.codeVerifier());

    var issued = tokenIssuer.issueCode(session, idToken);

    var redirectUri =
        UriBuilder.fromUri(session.redirectUri())
            .queryParam("code", issued.code())
            .queryParam("state", session.state())
            .build();

    return Response.seeOther(redirectUri).build();
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
