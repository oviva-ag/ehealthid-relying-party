package com.oviva.ehealthid.relyingparty.svc;

import com.oviva.ehealthid.auth.AuthenticationFlow;
import com.oviva.ehealthid.fedclient.IdpEntry;
import com.oviva.ehealthid.relyingparty.cfg.RelyingPartyConfig;
import com.oviva.ehealthid.relyingparty.fed.FederationConfig;
import com.oviva.ehealthid.relyingparty.svc.LocalizedException.Message;
import com.oviva.ehealthid.relyingparty.svc.OpenIdErrors.ErrorCode;
import com.oviva.ehealthid.relyingparty.svc.SessionRepo.Session;
import com.oviva.ehealthid.relyingparty.util.IdGenerator;
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

    // https://datatracker.ietf.org/doc/html/rfc7636#section-4.1
    var bytes = new byte[32];
    rng.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
            .appUri(request.appUri())
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
      throw new ValidationException(new Message("error.noProvider"));
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

    validateCallbackRequest(request);

    var session = mustFindSession(request.sessionId());

    var idToken =
        session
            .trustedSectoralIdpStep()
            .exchangeSectoralIdpCode(request.code(), session.codeVerifier());

    session = removeSession(request.sessionId());
    if (session == null) {
      throw new ValidationException(new Message("error.invalidSession"));
    }

    var issued = tokenIssuer.issueCode(session, idToken);

    return UriBuilder.fromUri(session.redirectUri())
        .queryParam("code", issued.code())
        .queryParam("state", session.state())
        .build();
  }

  private void validateCallbackRequest(CallbackRequest request) {
    if (request.code() == null
        || request.code().isBlank()
        || request.sessionId() == null
        || request.sessionId().isBlank()) {
      throw new ValidationException(new Message("error.invalidCallback"));
    }
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
    var localizedMessage = new Message("error.invalidSession");
    if (id == null || id.isBlank()) {
      throw new ValidationException(localizedMessage);
    }

    var session = sessionRepo.load(id);
    if (session == null) {
      throw new ValidationException(localizedMessage);
    }
    return session;
  }

  private void validateAuthorizationRequest(AuthorizationRequest request) {

    var redirect = request.redirectUri();

    if (redirect == null) {
      throw new ValidationException(new Message("error.noRedirect"));
    }

    if (!"https".equals(redirect.getScheme())) {
      var localizedMessage = new Message("error.insecureRedirect", redirect.toString());
      throw new ValidationException(localizedMessage);
    }

    if (!relyingPartyConfig.validRedirectUris().contains(redirect)) {
      var localizedMessage = new Message("error.untrustedRedirect", redirect.toString());
      throw new ValidationException(localizedMessage);
    }

    if (!"openid".equals(request.scope())) {
      var localizedErrorMessage = new Message("error.unsupportedScope", request.scope());
      var uri =
          OpenIdErrors.redirectWithError(
              redirect,
              ErrorCode.INVALID_SCOPE,
              request.state(),
              localizedErrorMessage.messageKey());
      throw new ValidationException(localizedErrorMessage, uri);
    }

    var responseType = request.responseType();
    if (responseType == null
        || !relyingPartyConfig.supportedResponseTypes().contains(responseType)) {
      var localizedErrorMessage = new Message("error.unsupportedResponseType", responseType);
      var uri =
          OpenIdErrors.redirectWithError(
              redirect,
              ErrorCode.UNSUPPORTED_RESPONSE_TYPE,
              request.state(),
              localizedErrorMessage.messageKey());
      throw new ValidationException(localizedErrorMessage, uri);
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
      URI appUri,
      String nonce) {}

  public record AuthorizationResponse(List<IdpEntry> identityProviders, String sessionId) {}
}
