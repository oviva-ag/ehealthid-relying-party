package com.oviva.gesundheitsid.auth.steps;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.crypto.MultiDecrypter;
import com.oviva.gesundheitsid.auth.IdTokenJWS;
import com.oviva.gesundheitsid.auth.IdTokenJWS.Payload;
import com.oviva.gesundheitsid.crypto.JwsVerifier;
import com.oviva.gesundheitsid.crypto.KeySupplier;
import com.oviva.gesundheitsid.fedclient.api.EntityStatementJWS;
import com.oviva.gesundheitsid.fedclient.api.OpenIdClient;
import com.oviva.gesundheitsid.util.JsonCodec;
import com.oviva.gesundheitsid.util.JsonPayloadTransformer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.net.URI;
import java.text.ParseException;

public class TrustedSectoralIdpStep {

  private final OpenIdClient openIdClient;

  private final URI selfIssuer;
  private final URI idpRedirectUri;
  private final URI callbackUri;
  private final EntityStatementJWS trustedIdpEntityStatement;
  private final KeySupplier relyingPartyEncKeySupplier;

  public TrustedSectoralIdpStep(
      OpenIdClient openIdClient,
      URI selfIssuer,
      URI idpRedirectUri,
      URI callbackUri,
      EntityStatementJWS trustedIdpEntityStatement,
      KeySupplier relyingPartyEncKeySupplier) {
    this.openIdClient = openIdClient;
    this.selfIssuer = selfIssuer;
    this.idpRedirectUri = idpRedirectUri;
    this.callbackUri = callbackUri;
    this.trustedIdpEntityStatement = trustedIdpEntityStatement;
    this.relyingPartyEncKeySupplier = relyingPartyEncKeySupplier;
  }

  public URI idpRedirectUri() {
    return idpRedirectUri;
  }

  public IdTokenJWS exchangeSectoralIdpCode(@NonNull String code, @NonNull String codeVerifier) {

    if (trustedIdpEntityStatement == null) {
      throw new IllegalStateException("flow has no trusted IDP statement, state not persisted?");
    }

    if (callbackUri == null) {
      throw new IllegalStateException("flow has no callback_uri, state not persisted?");
    }

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
      //      var decrypter = new ECDHDecrypter(relyingPartyEncKeySupplier.get().priv());
      var decrypter =
          new MultiDecrypter(relyingPartyEncKeySupplier.apply(jweObject.getHeader().getKeyID()));
      jweObject.decrypt(decrypter);

      var signedJws = jweObject.getPayload().toJWSObject();

      if (!JwsVerifier.verify(trustedIdpEntityStatement.body().jwks(), signedJws)) {
        throw new RuntimeException("bad signature from IDP on id token");
      }

      var payload =
          signedJws
              .getPayload()
              .toType(new JsonPayloadTransformer<>(Payload.class, JsonCodec::readValue));
      return new IdTokenJWS(signedJws, payload);

    } catch (JOSEException | ParseException e) {
      throw new RuntimeException(e);
    }
  }
}
