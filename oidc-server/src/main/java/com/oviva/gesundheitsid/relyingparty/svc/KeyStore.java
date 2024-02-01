package com.oviva.gesundheitsid.relyingparty.svc;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;

public class KeyStore {

  private final ECKey signingKey;

  public KeyStore() {

    try {
      this.signingKey =
          new ECKeyGenerator(Curve.P_256)
              .keyIDFromThumbprint(false)
              .keyUse(KeyUse.SIGNATURE)
              .generate();
    } catch (JOSEException e) {
      throw new IllegalStateException("failed to generate EC signing key", e);
    }
  }

  public ECKey signingKey() {
    return signingKey;
  }
}
