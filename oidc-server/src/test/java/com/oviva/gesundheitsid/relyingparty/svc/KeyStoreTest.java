package com.oviva.gesundheitsid.relyingparty.svc;

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.KeyUse;
import org.junit.jupiter.api.Test;

class KeyStoreTest {

  @Test
  void signingKey() throws JOSEException {

    var sut = new KeyStore();

    var key = sut.signingKey();

    assertEquals(KeyUse.SIGNATURE, key.getKeyUse());
    assertNotNull(key.toECPrivateKey());
  }
}
