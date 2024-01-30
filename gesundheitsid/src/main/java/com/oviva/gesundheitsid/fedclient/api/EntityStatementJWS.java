package com.oviva.gesundheitsid.fedclient.api;

import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.jwk.JWKSet;
import com.oviva.gesundheitsid.crypto.JwsVerifier;
import com.oviva.gesundheitsid.fedclient.FederationExceptions;
import com.oviva.gesundheitsid.util.JsonCodec;
import com.oviva.gesundheitsid.util.JsonPayloadTransformer;
import java.text.ParseException;
import java.time.Instant;

public record EntityStatementJWS(JWSObject jws, EntityStatement body) implements TemporalValid {

  private static final String ENTITY_STATEMENT_TYP = "entity-statement+jwt";

  public static EntityStatementJWS parse(String wire) {
    try {

      var jws = JWSObject.parse(wire);

      if (!ENTITY_STATEMENT_TYP.equals(jws.getHeader().getType().getType())) {
        throw FederationExceptions.notAnEntityStatement(jws.getHeader().getType().getType());
      }

      var es =
          jws.getPayload()
              .toType(new JsonPayloadTransformer<>(EntityStatement.class, JsonCodec::readValue));
      return new EntityStatementJWS(jws, es);
    } catch (ParseException e) {
      throw FederationExceptions.badEntityStatement(e);
    }
  }

  public boolean verifySelfSigned() {
    return verifySignature(body.jwks());
  }

  public boolean verifySignature(JWKSet jwks) {
    return JwsVerifier.verify(jwks, jws);
  }

  @Override
  public boolean isValidAt(Instant pointInTime) {
    var epoch = pointInTime.getEpochSecond();
    return body.nbf() < epoch && epoch < body.exp();
  }
}
