package com.oviva.gesundheitsid.crypto;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;
import com.nimbusds.jose.jwk.JWKSet;
import com.oviva.gesundheitsid.fedclient.FederationExceptions;
import edu.umd.cs.findbugs.annotations.NonNull;

public class JwsVerifier {

  private JwsVerifier() {}

  public static boolean verify(@NonNull JWKSet jwks, @NonNull JWSObject jws) {

    if (jwks == null) {
      throw new IllegalArgumentException("no JWKS provided to verify JWS");
    }

    if (jwks.getKeys() == null || jwks.getKeys().isEmpty()) {
      return false;
    }

    var header = jws.getHeader();
    if (!JWSAlgorithm.ES256.equals(header.getAlgorithm())) {
      throw new UnsupportedOperationException(
          "only supports ES256, found: " + header.getAlgorithm());
    }

    var key = jwks.getKeyByKeyId(header.getKeyID());
    if (key == null) {
      return false;
    }

    try {
      var processor = new DefaultJWSVerifierFactory();
      var verifier = processor.createJWSVerifier(jws.getHeader(), key.toECKey().toPublicKey());
      return jws.verify(verifier);
    } catch (JOSEException e) {
      throw FederationExceptions.badSignature(e);
    }
  }
}
