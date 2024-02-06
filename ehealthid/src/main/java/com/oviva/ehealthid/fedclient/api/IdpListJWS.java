package com.oviva.ehealthid.fedclient.api;

import com.nimbusds.jose.JWSObject;
import com.oviva.ehealthid.fedclient.FederationExceptions;
import com.oviva.ehealthid.util.JsonCodec;
import com.oviva.ehealthid.util.JsonPayloadTransformer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.text.ParseException;
import java.time.Instant;

public record IdpListJWS(JWSObject jws, IdpList body) implements TemporalValid {

  private static final String IDP_LIST_TYP = "idp-list+jwt";

  public static IdpListJWS parse(@NonNull String wire) {
    try {
      var jws = JWSObject.parse(wire);

      if (!IDP_LIST_TYP.equals(jws.getHeader().getType().getType())) {
        throw FederationExceptions.notAnIdpList(jws.getHeader().getType().getType());
      }

      var list =
          jws.getPayload()
              .toType(new JsonPayloadTransformer<>(IdpList.class, JsonCodec::readValue));
      return new IdpListJWS(jws, list);
    } catch (ParseException e) {
      throw FederationExceptions.badIdpList(e);
    }
  }

  @Override
  public boolean isValidAt(Instant pointInTime) {
    var epoch = pointInTime.getEpochSecond();
    return body.nbf() < epoch && epoch < body.exp();
  }
}
