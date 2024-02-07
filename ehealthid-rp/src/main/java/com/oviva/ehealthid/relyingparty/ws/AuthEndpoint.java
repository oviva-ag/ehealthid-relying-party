package com.oviva.ehealthid.relyingparty.ws;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oviva.ehealthid.auth.AuthenticationFlow;
import com.oviva.ehealthid.relyingparty.cfg.RelyingPartyConfig;
import com.oviva.ehealthid.relyingparty.svc.SessionRepo;
import com.oviva.ehealthid.relyingparty.svc.SessionRepo.Session;
import com.oviva.ehealthid.relyingparty.svc.TokenIssuer;
import com.oviva.ehealthid.relyingparty.util.IdGenerator;
import com.oviva.ehealthid.relyingparty.ws.OpenIdErrorResponses.ErrorCode;
import com.oviva.ehealthid.relyingparty.ws.ui.Pages;
import com.oviva.ehealthid.relyingparty.ws.ui.TemplateRenderer;
import edu.umd.cs.findbugs.annotations.Nullable;
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

  private final URI baseUri;
  private final RelyingPartyConfig relyingPartyConfig;

  private final SessionRepo sessionRepo;
  private final TokenIssuer tokenIssuer;

  private final AuthenticationFlow authenticationFlow;

  private final Pages pages = new Pages(new TemplateRenderer());

  public AuthEndpoint(
      URI baseUri,
      RelyingPartyConfig relyingPartyConfig,
      SessionRepo sessionRepo,
      TokenIssuer tokenIssuer,
      AuthenticationFlow authenticationFlow) {
    this.baseUri = baseUri;
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
      return badRequest(
          "Bad redirect_uri='%s'. Passed link is not valid.".formatted(parsedRedirect));
    }

    if (!"https".equals(parsedRedirect.getScheme())) {
      return badRequest(
          "Insecure redirect_uri='%s'. Misconfigured server, please use 'https'."
              .formatted(parsedRedirect));
    }

    if (!relyingPartyConfig.validRedirectUris().contains(parsedRedirect)) {
      return badRequest(
          "Untrusted redirect_uri=%s. Misconfigured server.".formatted(parsedRedirect));
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
    var relyingPartyCallback = baseUri.resolve("/auth/callback");

    var step1 =
        authenticationFlow.start(
            new AuthenticationFlow.Session(
                state, nonce, relyingPartyCallback, codeChallenge, scopes));

    // ==== 2) get the list of available IDPs
    var identityProviders = step1.fetchIdpOptions();
    var form = pages.selectIdpForm(identityProviders);

    // store session
    var sessionId = IdGenerator.generateID();
    var session =
        new Session(sessionId, state, nonce, parsedRedirect, clientId, verifier, step1, null);
    sessionRepo.save(session);

    return Response.ok(form, MediaType.TEXT_HTML_TYPE)
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
  @Path("/select-idp")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response postSelectIdp(
      @CookieParam("session_id") String sessionId,
      @FormParam("identityProvider") String identityProvider) {

    if (identityProvider == null || identityProvider.isBlank()) {
      return badRequest("No identity provider selected. Please go back.");
    }

    var session = findSession(sessionId);
    if (session == null) {
      return badRequest("Oops, no session unknown or expired. Please start again.");
    }

    var step2 = session.selectSectoralIdpStep().redirectToSectoralIdp(identityProvider);

    var federatedLogin = step2.idpRedirectUri();

    var newSession =
        new SessionRepo.Session(
            session.id(),
            session.state(),
            session.nonce(),
            session.redirectUri(),
            session.clientId(),
            session.codeVerifier(),
            session.selectSectoralIdpStep(),
            step2);

    sessionRepo.save(newSession);

    return Response.seeOther(federatedLogin).build();
  }

  @GET
  @Path("/callback")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response callback(
      @CookieParam("session_id") String sessionId, @QueryParam("code") String code) {

    var session = findSession(sessionId);
    if (session == null) {
      return badRequest("Oops, no session unknown or expired. Please start again.");
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

  @Nullable
  private Session findSession(@Nullable String id) {

    if (id == null || id.isBlank()) {
      return null;
    }

    return sessionRepo.load(id);
  }

  private Response badRequest(String message) {
    return Response.status(Status.BAD_REQUEST)
        .entity(pages.error(message))
        .type(MediaType.TEXT_HTML_TYPE)
        .build();
  }

  public record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("token_type") String tokenType,
      @JsonProperty("refresh_token") String refreshToken,
      @JsonProperty("expires_in") int expiresIn,
      @JsonProperty("id_token") String idToken) {}
}
