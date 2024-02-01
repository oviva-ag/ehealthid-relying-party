package com.oviva.gesundheitsid.auth.internal.steps;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.crypto.MultiDecrypter;
import com.oviva.gesundheitsid.auth.AuthExceptions;
import com.oviva.gesundheitsid.auth.IdTokenJWS;
import com.oviva.gesundheitsid.auth.IdTokenJWS.IdToken;
import com.oviva.gesundheitsid.auth.steps.TrustedSectoralIdpStep;
import com.oviva.gesundheitsid.crypto.JwsVerifier;
import com.oviva.gesundheitsid.crypto.KeySupplier;
import com.oviva.gesundheitsid.fedclient.api.EntityStatementJWS;
import com.oviva.gesundheitsid.fedclient.api.OpenIdClient;
import com.oviva.gesundheitsid.util.JsonCodec;
import com.oviva.gesundheitsid.util.JsonPayloadTransformer;
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

  public TrustedSectoralIdpStepImpl(
      @NonNull OpenIdClient openIdClient,
      @NonNull URI selfIssuer,
      @NonNull URI idpRedirectUri,
      @NonNull URI callbackUri,
      @NonNull EntityStatementJWS trustedIdpEntityStatement,
      @NonNull KeySupplier relyingPartyEncKeySupplier) {
    this.openIdClient = openIdClient;
    this.selfIssuer = selfIssuer;
    this.idpRedirectUri = idpRedirectUri;
    this.callbackUri = callbackUri;
    this.trustedIdpEntityStatement = trustedIdpEntityStatement;
    this.relyingPartyEncKeySupplier = relyingPartyEncKeySupplier;
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

      if (!JwsVerifier.verify(trustedIdpEntityStatement.body().jwks(), signedJws)) {
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
