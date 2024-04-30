package com.oviva.ehealthid.relyingparty.util;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.ECKey;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.security.*;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;

public class TlsContext {
  @NonNull
  public static SSLContext fromClientCertificate(@NonNull ECKey ecKey) {
    // https://connect2id.com/products/nimbus-oauth-openid-connect-sdk/examples/utils/custom-key-store

    if (ecKey.getParsedX509CertChain() == null || ecKey.getParsedX509CertChain().isEmpty()) {
      throw new IllegalArgumentException(
          "client key is missing certificate, kid: " + ecKey.getKeyID());
    }

    try {
      var ctx = SSLContext.getInstance("TLS");

      var tmf = TrustManagerFactory.getInstance("PKIX");

      // Using null here initialises the TMF with the default trust store.
      tmf.init((KeyStore) null);

      ctx.init(
          keyManagerOf(ecKey.getParsedX509CertChain().get(0), ecKey.toPrivateKey()),
          tmf.getTrustManagers(),
          null);
      return ctx;
    } catch (JOSEException | GeneralSecurityException e) {
      throw new IllegalStateException("failed to initialize SSL context", e);
    }
  }

  private static KeyManager[] keyManagerOf(X509Certificate cert, PrivateKey privateKey) {

    var pw = new char[0];

    try {
      var ks = KeyStore.getInstance(KeyStore.getDefaultType());
      ks.load(null);
      ks.setKeyEntry("client-auth", privateKey, pw, new java.security.cert.Certificate[] {cert});
      var kmf = KeyManagerFactory.getInstance("PKIX");
      kmf.init(ks, pw);
      return kmf.getKeyManagers();
    } catch (IOException | GeneralSecurityException e) {
      throw new IllegalArgumentException("failed to initialize client certificate store", e);
    }
  }
}
