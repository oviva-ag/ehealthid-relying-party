package com.oviva.ehealthid.fedclient.api;

import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.jwk.JWKSet;
import com.oviva.ehealthid.crypto.JwsVerifier;
import com.oviva.ehealthid.fedclient.FederationExceptions;
import com.oviva.ehealthid.util.JsonCodec;
import com.oviva.ehealthid.util.JsonPayloadTransformer;
import java.text.ParseException;
import java.time.Instant;

public record ExtendedJWKSetJWS(JWSObject jws, ExtendedJWKSet body) implements TemporalValid {

  public static final String JWKS_TYPE = "jwk-set+json";

  public static ExtendedJWKSetJWS parse(String wire) {
    try {

      var jws = JWSObject.parse(wire);

      if (!JWKS_TYPE.equals(jws.getHeader().getType().getType())) {
        throw FederationExceptions.notASignedJwks(jws.getHeader().getType().getType());
      }

      var es =
          jws.getPayload()
              .toType(new JsonPayloadTransformer<>(ExtendedJWKSet.class, JsonCodec::readValue));
      return new ExtendedJWKSetJWS(jws, es);
    } catch (ParseException e) {
      throw FederationExceptions.badSignedJwks(e);
    }
  }

  public boolean verifySignature(JWKSet jwks) {
    return JwsVerifier.verify(jwks, jws);
  }

  @Override
  public boolean isValidAt(Instant pointInTime) {
    var epoch = pointInTime.getEpochSecond();
    return body.exp() == 0 || epoch < body.exp();
  }
}
