package com.oviva.ehealthid.relyingparty.svc;

import com.oviva.ehealthid.auth.AuthenticationFlow;
import com.oviva.ehealthid.fedclient.IdpEntry;
import com.oviva.ehealthid.relyingparty.cfg.RelyingPartyConfig;
import com.oviva.ehealthid.relyingparty.fed.FederationConfig;
import com.oviva.ehealthid.relyingparty.svc.OpenIdErrors.ErrorCode;
import com.oviva.ehealthid.relyingparty.svc.SessionRepo.Session;
import com.oviva.ehealthid.relyingparty.util.IdGenerator;
import com.oviva.ehealthid.relyingparty.ws.ui.Pages;
import com.oviva.ehealthid.relyingparty.ws.ui.TemplateRenderer;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import jakarta.ws.rs.core.UriBuilder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

public class AuthService {

  private final URI baseUri;
  private final RelyingPartyConfig relyingPartyConfig;

  private final SessionRepo sessionRepo;
  private final TokenIssuer tokenIssuer;

  private final AuthenticationFlow authenticationFlow;

  private final Pages pages = new Pages(new TemplateRenderer());

  private final FederationConfig federationConfig;

  public AuthService(
      URI baseUri,
      RelyingPartyConfig relyingPartyConfig,
      FederationConfig federationConfig,
      SessionRepo sessionRepo,
      TokenIssuer tokenIssuer,
      AuthenticationFlow authenticationFlow) {
    this.baseUri = baseUri;
    this.relyingPartyConfig = relyingPartyConfig;
    this.federationConfig = federationConfig;
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

  private static String generatePkceCodeVerifier() {
    var rng = new SecureRandom();

    var bytes = new byte[32];
    rng.nextBytes(bytes);
    return Base64.getUrlEncoder().encodeToString(bytes);
  }

  // Authorization Request
  // https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.1
  @NonNull
  public AuthorizationResponse auth(@NonNull AuthorizationRequest request) {

    validateAuthorizationRequest(request);

    var verifier = generatePkceCodeVerifier();

    var codeChallenge = calculateS256CodeChallenge(verifier);

    var relyingPartyCallback = baseUri.resolve("/auth/callback");

    var step1 =
        authenticationFlow.start(
            new AuthenticationFlow.Session(
                request.state(),
                request.nonce(),
                relyingPartyCallback,
                codeChallenge,
                federationConfig.scopes()));

    var identityProviders = step1.fetchIdpOptions();

    var sessionId = IdGenerator.generateID();
    var session =
        Session.create()
            .id(sessionId)
            .state(request.state())
            .nonce(request.nonce())
            .redirectUri(request.redirectUri())
            .clientId(request.clientId())
            .codeVerifier(verifier)
            .selectSectoralIdpStep(step1)
            .build();

    sessionRepo.save(session);

    return new AuthorizationResponse(identityProviders, sessionId);
  }

  @NonNull
  public URI selectedIdentityProvider(@NonNull SelectedIdpRequest request) {

    var selectedIdp = request.selectedIdentityProvider();
    if (selectedIdp == null || selectedIdp.isBlank()) {
      throw new ValidationException("No identity provider selected. Please go back.");
    }

    var session = mustFindSession(request.sessionId());

    var step2 = session.selectSectoralIdpStep().redirectToSectoralIdp(selectedIdp);

    var federatedLogin = step2.idpRedirectUri();

    var newSession = session.toBuilder().trustedSectoralIdpStep(step2).build();

    sessionRepo.save(newSession);

    return federatedLogin;
  }

  @NonNull
  public URI callback(@NonNull CallbackRequest request) {

    var session = mustFindSession(request.sessionId());

    var idToken =
        session
            .trustedSectoralIdpStep()
            .exchangeSectoralIdpCode(request.code(), session.codeVerifier());

    session = removeSession(request.sessionId());
    if (session == null) {
      throw new ValidationException("Oops, session unknown or expired. Please start again.");
    }

    var issued = tokenIssuer.issueCode(session, idToken);

    return UriBuilder.fromUri(session.redirectUri())
        .queryParam("code", issued.code())
        .queryParam("state", session.state())
        .build();
  }

  @Nullable
  private Session removeSession(@Nullable String id) {

    if (id == null || id.isBlank()) {
      return null;
    }

    return sessionRepo.remove(id);
  }

  @NonNull
  private Session mustFindSession(@Nullable String id) {
    var msgNoSessionFound = "Oops, no session unknown or expired. Please start again.";
    if (id == null || id.isBlank()) {
      throw new ValidationException(msgNoSessionFound);
    }

    var session = sessionRepo.load(id);
    if (session == null) {
      throw new ValidationException(msgNoSessionFound);
    }
    return session;
  }

  private void validateAuthorizationRequest(AuthorizationRequest request) {

    var redirect = request.redirectUri();

    if (redirect == null) {
      throw new ValidationException("no redirect_uri");
    }

    if (!"https".equals(redirect.getScheme())) {
      throw new ValidationException(
          "Insecure redirect_uri='%s'. Misconfigured server, please use 'https'."
              .formatted(redirect));
    }

    if (!relyingPartyConfig.validRedirectUris().contains(redirect)) {
      throw new ValidationException(
          "Untrusted redirect_uri=%s. Misconfigured server.".formatted(redirect));
    }

    if (!"openid".equals(request.scope())) {
      var msg = "scope '%s' not supported".formatted(request.scope());
      var uri =
          OpenIdErrors.redirectWithError(redirect, ErrorCode.INVALID_SCOPE, request.state(), msg);
      throw new ValidationException(msg, uri);
    }

    if (!relyingPartyConfig.supportedResponseTypes().contains(request.responseType())) {
      var msg = "unsupported response type: '%s'".formatted(request.responseType());
      var uri =
          OpenIdErrors.redirectWithError(
              redirect, ErrorCode.UNSUPPORTED_RESPONSE_TYPE, request.state(), msg);
      throw new ValidationException(msg, uri);
    }
  }

  public record SelectedIdpRequest(String sessionId, String selectedIdentityProvider) {}

  public record CallbackRequest(String sessionId, String code) {}

  public record AuthorizationRequest(
      String scope,
      String state,
      String responseType,
      String clientId,
      URI redirectUri,
      String nonce) {}

  public record AuthorizationResponse(List<IdpEntry> identityProviders, String sessionId) {}
}
