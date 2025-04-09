package com.oviva.ehealthid.fedclient.api;

import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.jwk.JWKSet;
import com.oviva.ehealthid.crypto.JwsVerifier;
import com.oviva.ehealthid.fedclient.FederationExceptions;
import com.oviva.ehealthid.util.JsonCodec;
import com.oviva.ehealthid.util.JsonPayloadTransformer;
import java.text.ParseException;
import java.time.Instant;

public record EntityStatementJWS(JWSObject jws, EntityStatement body) implements TemporalValid {

  public static final String ENTITY_STATEMENT_TYP = "entity-statement+jwt";

  public static EntityStatementJWS parse(String wire) {
    try {

      var jws = JWSObject.parse(wire);

      if (!isValidTyp(jws)) {
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

  private static boolean isValidTyp(JWSObject jws) {

    if (jws.getHeader() == null || jws.getHeader().getType() == null) {
      return false;
    }
    var typ = jws.getHeader().getType().getType();

    return ENTITY_STATEMENT_TYP.equals(typ);
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
