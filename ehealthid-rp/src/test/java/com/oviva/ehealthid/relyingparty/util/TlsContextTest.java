package com.oviva.ehealthid.relyingparty.util;

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.oauth2.sdk.util.X509CertificateUtils;
import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.security.Principal;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509KeyManager;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.Test;

class TlsContextTest {

  private static final String ISSUER = "https://example.com";

  @Test
  void fromClientCertificate_smoke() throws Exception {
    var key = generateSigningKey(URI.create(ISSUER));

    var ctx = TlsContext.fromClientCertificate(key);
    assertNotNull(ctx);
  }

  @Test
  void fromClientCertificate_noX509() throws Exception {
    var key =
        new ECKeyGenerator(Curve.P_256)
            .keyUse(KeyUse.SIGNATURE)
            .keyIDFromThumbprint(true)
            .generate();

    assertThrows(IllegalArgumentException.class, () -> TlsContext.fromClientCertificate(key));
  }

  @Test
  void fromClientCertificate_noPrivate() throws Exception {
    var key = generateSigningKey(URI.create(ISSUER));
    var pub = key.toPublicJWK();

    assertThrows(IllegalArgumentException.class, () -> TlsContext.fromClientCertificate(pub));
  }

  @Test
  void keyManager() throws Exception {

    var key = generateSigningKey(URI.create(ISSUER));

    // when
    var kms = TlsContext.keyManagerOf(key.getParsedX509CertChain().get(0), key.toPrivateKey());

    // then
    assertContainsCert(kms, key);
  }

  void assertContainsCert(KeyManager[] kms, ECKey key) throws JOSEException {

    assertEquals(1, kms.length);
    var km = kms[0];

    assertInstanceOf(X509KeyManager.class, km);
    var x5km = (X509KeyManager) km;

    var aliases = x5km.getClientAliases("EC", new Principal[] {new X500Principal("CN=" + ISSUER)});
    assertEquals(1, aliases.length);

    var chain = x5km.getCertificateChain(aliases[0]);

    var cert = chain[0];
    assertEquals(key.toPublicKey(), cert.getPublicKey());
  }

  private ECKey generateSigningKey(URI issuer)
      throws JOSEException, IOException, CertificateEncodingException, OperatorCreationException {

    var key =
        new ECKeyGenerator(Curve.P_256)
            .keyUse(KeyUse.SIGNATURE)
            .keyIDFromThumbprint(true)
            .generate();

    var now = Instant.now();
    var nbf = now.minus(Duration.ofHours(24));
    var exp = now.plus(Duration.ofDays(180));

    var cert =
        X509CertificateUtils.generateSelfSigned(
            new Issuer(issuer),
            Date.from(nbf),
            Date.from(exp),
            key.toPublicKey(),
            key.toPrivateKey());

    return new ECKey.Builder(key).x509CertChain(List.of(Base64.encode(cert.getEncoded()))).build();
  }

  private static class NaiveTrustManager extends X509ExtendedTrustManager {
    @Override
    public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
    }

    @Override
    public void checkClientTrusted(X509Certificate[] certs, String authType) {}

    @Override
    public void checkServerTrusted(X509Certificate[] certs, String authType) {}

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
        throws CertificateException {}

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
        throws CertificateException {}

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
        throws CertificateException {}

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
        throws CertificateException {}
  }
}
