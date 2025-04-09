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
  private static final boolean LENIENT = true;

  public static ExtendedJWKSetJWS parse(String wire) {
    try {

      var jws = JWSObject.parse(wire);

      if (!isValidTyp(jws)) {
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

  private static boolean isValidTyp(JWSObject jws) {
    // GemSpec and OpenID Spec disagree
    // according to OpenID spec this is a MUST and GemSpec allows it blank
    // https://gemspec.gematik.de/docs/gemSpec/gemSpec_IDP_Sek/gemSpec_IDP_Sek_V2.6.0
    // https://openid.net/specs/openid-federation-1_0.html#section-5.2.1
    if (LENIENT) {
      return true;
    }

    if (jws.getHeader() == null || jws.getHeader().getType() == null) {
      return false;
    }
    var typ = jws.getHeader().getType().getType();

    return JWKS_TYPE.equals(typ);
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
