package com.oviva.gesundheitsid.auth.internal.steps;

import com.oviva.gesundheitsid.auth.AuthExceptions;
import com.oviva.gesundheitsid.auth.steps.SelectSectoralIdpStep;
import com.oviva.gesundheitsid.auth.steps.TrustedSectoralIdpStep;
import com.oviva.gesundheitsid.crypto.KeySupplier;
import com.oviva.gesundheitsid.fedclient.FederationMasterClient;
import com.oviva.gesundheitsid.fedclient.IdpEntry;
import com.oviva.gesundheitsid.fedclient.api.EntityStatement;
import com.oviva.gesundheitsid.fedclient.api.EntityStatement.OpenidProvider;
import com.oviva.gesundheitsid.fedclient.api.OpenIdClient;
import com.oviva.gesundheitsid.fedclient.api.OpenIdClient.ParResponse;
import com.oviva.gesundheitsid.fedclient.api.ParBodyBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import jakarta.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.List;

/**
 * Official documentation: -
 * https://wiki.gematik.de/display/IDPKB/App-App+Flow#AppAppFlow-0-FederationMaster
 */
public class SelectSectoralIdpStepImpl implements SelectSectoralIdpStep {

  private final URI selfIssuer;
  private final FederationMasterClient fedMasterClient;
  private final OpenIdClient openIdClient;
  private final KeySupplier relyingPartyEncKeySupplier;

  private final URI callbackUri;
  private final String nonce;
  private final String codeChallengeS256;
  private final String state;
  private final List<String> scopes;

  public SelectSectoralIdpStepImpl(
      URI selfIssuer,
      FederationMasterClient fedMasterClient,
      OpenIdClient openIdClient,
      KeySupplier relyingPartyEncKeySupplier1,
      URI callbackUri,
      String nonce,
      String codeChallengeS256,
      String state,
      List<String> scopes) {
    this.selfIssuer = selfIssuer;
    this.fedMasterClient = fedMasterClient;
    this.openIdClient = openIdClient;
    this.relyingPartyEncKeySupplier = relyingPartyEncKeySupplier1;
    this.callbackUri = callbackUri;
    this.nonce = nonce;
    this.codeChallengeS256 = codeChallengeS256;
    this.state = state;
    this.scopes = scopes;
  }

  @NonNull
  @Override
  public List<IdpEntry> fetchIdpOptions() {
    return fedMasterClient.listAvailableIdps();
  }

  @Override
  public @NonNull TrustedSectoralIdpStep redirectToSectoralIdp(@NonNull String sectoralIdpIss) {

    var trustedIdpEntityStatement = fedMasterClient.establishIdpTrust(URI.create(sectoralIdpIss));

    // start PAR with sectoral IdP
    // https://datatracker.ietf.org/doc/html/rfc9126

    var parBody =
        ParBodyBuilder.create()
            .clientId(selfIssuer.toString())
            .codeChallenge(codeChallengeS256)
            .codeChallengeMethod("S256")
            .redirectUri(callbackUri)
            .nonce(nonce)
            .state(state)
            .scopes(scopes)
            .acrValues("gematik-ehealth-loa-high")
            .responseType("code");

    var res = doPushedAuthorizationRequest(parBody, trustedIdpEntityStatement.body());

    var redirectUri = buildAuthorizationUrl(res.requestUri(), trustedIdpEntityStatement.body());

    return new TrustedSectoralIdpStepImpl(
        openIdClient,
        selfIssuer,
        redirectUri,
        callbackUri,
        trustedIdpEntityStatement,
        relyingPartyEncKeySupplier);
  }

  private URI buildAuthorizationUrl(String parRequestUri, EntityStatement trustedEntityStatement) {

    if (parRequestUri == null || parRequestUri.isBlank()) {
      throw AuthExceptions.invalidParRequestUri(parRequestUri);
    }

    var openidConfig = getIdpOpenIdProvider(trustedEntityStatement);
    var authzEndpoint = openidConfig.authorizationEndpoint();

    if (authzEndpoint == null || authzEndpoint.isBlank()) {
      throw AuthExceptions.missingAuthorizationUrl(trustedEntityStatement.sub());
    }

    return UriBuilder.fromUri(authzEndpoint)
        .queryParam("request_uri", parRequestUri)
        .queryParam("client_id", selfIssuer.toString())
        .build();
  }

  private ParResponse doPushedAuthorizationRequest(
      ParBodyBuilder builder, EntityStatement trustedEntityStatement) {

    var openidConfig = getIdpOpenIdProvider(trustedEntityStatement);
    var parEndpoint = openidConfig.pushedAuthorizationRequestEndpoint();
    if (parEndpoint == null || parEndpoint.isBlank()) {
      throw AuthExceptions.missingPARUrl(trustedEntityStatement.sub());
    }

    return openIdClient.requestPushedUri(URI.create(parEndpoint), builder);
  }

  private OpenidProvider getIdpOpenIdProvider(
      @NonNull EntityStatement trustedIdpEntityConfiguration) {

    if (trustedIdpEntityConfiguration.metadata() == null
        || trustedIdpEntityConfiguration.metadata().openidProvider() == null) {
      throw AuthExceptions.missingOpenIdConfigurationInEntityStatement(
          trustedIdpEntityConfiguration.sub());
    }

    return trustedIdpEntityConfiguration.metadata().openidProvider();
  }
}
