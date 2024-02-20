package com.oviva.ehealthid.relyingparty.ws;

import com.oviva.ehealthid.auth.AuthenticationFlow;
import com.oviva.ehealthid.fedclient.IdpEntry;
import com.oviva.ehealthid.relyingparty.cfg.RelyingPartyConfig;
import com.oviva.ehealthid.relyingparty.fed.FederationConfig;
import com.oviva.ehealthid.relyingparty.svc.SessionRepo;
import com.oviva.ehealthid.relyingparty.svc.SessionRepo.Session;
import com.oviva.ehealthid.relyingparty.svc.TokenIssuer;
import com.oviva.ehealthid.relyingparty.util.IdGenerator;
import com.oviva.ehealthid.relyingparty.ws.OpenIdErrorResponses.ErrorCode;
import com.oviva.ehealthid.relyingparty.ws.ui.Pages;
import com.oviva.ehealthid.relyingparty.ws.ui.TemplateRenderer;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micrometer.core.instrument.Counter;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import jakarta.ws.rs.*;
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
import java.util.Map;
import java.util.Optional;

@Produces({MediaType.TEXT_HTML, MediaType.APPLICATION_JSON})
@Path("/auth")
public class AuthEndpoint {

  private final URI baseUri;
  private final RelyingPartyConfig relyingPartyConfig;

  private final SessionRepo sessionRepo;
  private final TokenIssuer tokenIssuer;

  private final AuthenticationFlow authenticationFlow;

  private final Pages pages = new Pages(new TemplateRenderer());

  private final Counter authRequests;
  private final Counter authSuccesses;
  private final FederationConfig federationConfig;

  public AuthEndpoint(
      URI baseUri,
      RelyingPartyConfig relyingPartyConfig,
      FederationConfig federationConfig,
      SessionRepo sessionRepo,
      TokenIssuer tokenIssuer,
      AuthenticationFlow authenticationFlow,
      PrometheusMeterRegistry prometheusMeterRegistry) {
    this.baseUri = baseUri;
    this.relyingPartyConfig = relyingPartyConfig;
    this.federationConfig = federationConfig;
    this.sessionRepo = sessionRepo;
    this.tokenIssuer = tokenIssuer;
    this.authenticationFlow = authenticationFlow;
    this.authRequests = prometheusMeterRegistry.counter("auth.requests");
    this.authSuccesses = prometheusMeterRegistry.counter("auth.requests.success");
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
  @Produces(MediaType.TEXT_HTML)
  public Response auth(
      @QueryParam("scope") String scope,
      @QueryParam("state") String state,
      @QueryParam("response_type") String responseType,
      @QueryParam("client_id") String clientId,
      @QueryParam("redirect_uri") String redirectUri,
      @QueryParam("nonce") String nonce) {
    authRequests.increment();

    URI parsedRedirect = null;
    try {
      parsedRedirect = parseAndValidateRedirect(redirectUri);
    } catch (BadRequestException e) {
      return badRequest(e.getMessage());
    }

    var maybeError = validateOpenIdRequestSettings(parsedRedirect, scope, state, responseType);
    if (maybeError.isPresent()) {
      return maybeError.get();
    }

    FederatedFlowResult result = federatedFlow(state, clientId, nonce, parsedRedirect);

    var form = pages.selectIdpForm(result.identityProviders());

    return Response.ok(form, MediaType.TEXT_HTML_TYPE)
        .cookie(createSessionCookie(result.sessionId()))
        .build();
  }

  private FederatedFlowResult federatedFlow(
      String state, String clientId, String nonce, URI parsedRedirect) {
    var verifier = generateCodeVerifier(); // for PKCE
    // these should come from the client in the real world
    var codeChallenge = calculateS256CodeChallenge(verifier);

    // ==== 1) start a new flow
    var relyingPartyCallback = baseUri.resolve("/auth/callback");

    var step1 =
        authenticationFlow.start(
            new AuthenticationFlow.Session(
                state, nonce, relyingPartyCallback, codeChallenge, federationConfig.scopes()));

    // ==== 2) get the list of available IDPs
    var identityProviders = step1.fetchIdpOptions();

    // store session
    var sessionId = IdGenerator.generateID();

    var session =
        Session.create()
            .id(sessionId)
            .state(state)
            .nonce(nonce)
            .redirectUri(parsedRedirect)
            .clientId(clientId)
            .codeVerifier(verifier)
            .selectSectoralIdpStep(step1)
            .build();

    sessionRepo.save(session);

    return new FederatedFlowResult(sessionId, identityProviders);
  }

  private record FederatedFlowResult(String sessionId, List<IdpEntry> identityProviders) {}

  private URI parseAndValidateRedirect(String redirectUri) throws BadRequestException {
    URI parsedRedirect = null;
    try {
      parsedRedirect = new URI(redirectUri);
    } catch (URISyntaxException e) {
      throw new BadRequestException(
          "Bad redirect_uri='%s'. Passed link is not valid.".formatted(redirectUri));
    }

    if (!"https".equals(parsedRedirect.getScheme())) {
      throw new BadRequestException(
          "Insecure redirect_uri='%s'. Misconfigured server, please use 'https'."
              .formatted(parsedRedirect));
    }

    if (!relyingPartyConfig.validRedirectUris().contains(parsedRedirect)) {
      throw new BadRequestException(
          "Untrusted redirect_uri='%s'. Misconfigured server.".formatted(parsedRedirect));
    }
    return parsedRedirect;
  }

  private Optional<Response> validateOpenIdRequestSettings(
      URI parsedRedirect, String scope, String state, String responseType) {
    if (!"openid".equals(scope)) {
      return Optional.ofNullable(
          OpenIdErrorResponses.redirectWithError(
              parsedRedirect,
              ErrorCode.INVALID_SCOPE,
              state,
              "scope '%s' not supported".formatted(scope)));
    }

    if (!relyingPartyConfig.supportedResponseTypes().contains(responseType)) {
      return Optional.ofNullable(
          OpenIdErrorResponses.redirectWithError(
              parsedRedirect,
              ErrorCode.UNSUPPORTED_RESPONSE_TYPE,
              state,
              "unsupported response type: '%s'".formatted(responseType)));
    }

    return Optional.empty();
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response authJSON(
      @QueryParam("scope") String scope,
      @QueryParam("state") String state,
      @QueryParam("response_type") String responseType,
      @QueryParam("client_id") String clientId,
      @QueryParam("redirect_uri") String redirectUri,
      @QueryParam("nonce") String nonce) {
    // TODO: implement and test
    authRequests.increment();

    URI parsedRedirect = null;
    try {
      parsedRedirect = parseAndValidateRedirect(redirectUri);
    } catch (BadRequestException e) {
      return badRequestJSON(e.getMessage());
    }

    var maybeError = validateOpenIdRequestSettings(parsedRedirect, scope, state, responseType);
    if (maybeError.isPresent()) {
      return maybeError.get();
    }

    FederatedFlowResult result = federatedFlow(state, clientId, nonce, parsedRedirect);

    return Response.ok(Map.of("idps", result.identityProviders()), MediaType.APPLICATION_JSON_TYPE)
        .cookie(createSessionCookie(result.sessionId()))
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

    var newSession = session.toBuilder().trustedSectoralIdpStep(step2).build();

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
      return badRequest("Oops, session unknown or expired. Please start again.");
    }

    var idToken =
        session.trustedSectoralIdpStep().exchangeSectoralIdpCode(code, session.codeVerifier());

    session = removeSession(sessionId);
    if (session == null) {
      return badRequest("Oops, session unknown or expired. Please start again.");
    }

    var issued = tokenIssuer.issueCode(session, idToken);

    var redirectUri =
        UriBuilder.fromUri(session.redirectUri())
            .queryParam("code", issued.code())
            .queryParam("state", session.state())
            .build();

    authSuccesses.increment();
    return Response.seeOther(redirectUri).build();
  }

  @Nullable
  private Session removeSession(@Nullable String id) {

    if (id == null || id.isBlank()) {
      return null;
    }

    return sessionRepo.remove(id);
  }

  @Nullable
  private Session findSession(@Nullable String id) {

    if (id == null || id.isBlank()) {
      return null;
    }

    return sessionRepo.load(id);
  }

  @Produces(MediaType.TEXT_HTML)
  private Response badRequest(String message) {
    return Response.status(Status.BAD_REQUEST)
        .entity(pages.error(message))
        .type(MediaType.TEXT_HTML_TYPE)
        .build();
  }

  @Produces(MediaType.APPLICATION_JSON)
  private Response badRequestJSON(String message) {
    return Response.status(Status.BAD_REQUEST)
        .entity(Map.of("error", message))
        .type(MediaType.APPLICATION_JSON)
        .build();
  }
}
