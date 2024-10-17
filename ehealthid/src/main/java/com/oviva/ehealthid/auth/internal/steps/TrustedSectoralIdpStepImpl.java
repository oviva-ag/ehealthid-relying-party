package com.oviva.ehealthid.auth.internal.steps;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.crypto.MultiDecrypter;
import com.oviva.ehealthid.auth.AuthExceptions;
import com.oviva.ehealthid.auth.IdTokenJWS;
import com.oviva.ehealthid.auth.IdTokenJWS.IdToken;
import com.oviva.ehealthid.auth.steps.TrustedSectoralIdpStep;
import com.oviva.ehealthid.crypto.JwsVerifier;
import com.oviva.ehealthid.crypto.KeySupplier;
import com.oviva.ehealthid.fedclient.FederationMasterClient;
import com.oviva.ehealthid.fedclient.api.EntityStatementJWS;
import com.oviva.ehealthid.fedclient.api.OpenIdClient;
import com.oviva.ehealthid.util.JsonCodec;
import com.oviva.ehealthid.util.JsonPayloadTransformer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.net.URI;
import java.text.ParseException;

public class TrustedSectoralIdpStepImpl implements TrustedSectoralIdpStep {

  private final OpenIdClient openIdClient;

  private final URI selfIssuer;
  private final URI idpRedirectUri;
  private final URI callbackUri;
  private final EntityStatementJWS trustedIdpEntityStatement;
  private final KeySupplier relyingPartyEncKeySupplier;
  private final FederationMasterClient federationMasterClient;

  public TrustedSectoralIdpStepImpl(
      @NonNull OpenIdClient openIdClient,
      @NonNull URI selfIssuer,
      @NonNull URI idpRedirectUri,
      @NonNull URI callbackUri,
      @NonNull EntityStatementJWS trustedIdpEntityStatement,
      @NonNull KeySupplier relyingPartyEncKeySupplier,
      @NonNull FederationMasterClient federationMasterClient) {
    this.openIdClient = openIdClient;
    this.selfIssuer = selfIssuer;
    this.idpRedirectUri = idpRedirectUri;
    this.callbackUri = callbackUri;
    this.trustedIdpEntityStatement = trustedIdpEntityStatement;
    this.relyingPartyEncKeySupplier = relyingPartyEncKeySupplier;
    this.federationMasterClient = federationMasterClient;
  }

  @Override
  public @NonNull URI idpRedirectUri() {
    return idpRedirectUri;
  }

  @NonNull
  @Override
  public IdTokenJWS exchangeSectoralIdpCode(@NonNull String code, @NonNull String codeVerifier) {

    var tokenEndpoint =
        trustedIdpEntityStatement.body().metadata().openidProvider().tokenEndpoint();
    var res =
        openIdClient.exchangePkceCode(
            URI.create(tokenEndpoint),
            code,
            callbackUri.toString(),
            selfIssuer.toString(),
            codeVerifier);

    try {
      var jweObject = JWEObject.parse(res.idToken());
      var decrypter =
          new MultiDecrypter(relyingPartyEncKeySupplier.apply(jweObject.getHeader().getKeyID()));
      jweObject.decrypt(decrypter);

      var signedJws = jweObject.getPayload().toJWSObject();

      var idpSigningKeys =
          federationMasterClient.resolveOpenIdProviderJwks(trustedIdpEntityStatement);
      if (!JwsVerifier.verify(idpSigningKeys, signedJws)) {
        throw AuthExceptions.badIdTokenSignature(trustedIdpEntityStatement.body().sub());
      }

      var payload =
          signedJws
              .getPayload()
              .toType(new JsonPayloadTransformer<>(IdToken.class, JsonCodec::readValue));
      return new IdTokenJWS(signedJws, payload);

    } catch (JOSEException | ParseException e) {
      throw AuthExceptions.badIdToken(trustedIdpEntityStatement.body().sub(), e);
    }
  }
}
