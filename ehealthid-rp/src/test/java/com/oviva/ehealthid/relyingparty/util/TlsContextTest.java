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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("TODO")
class TlsContextTest {

  @Test
  void t() throws Exception {

    var key = generateSigningKey(URI.create("https://localhost:4443"));

    var sslContext = TlsContext.fromClientCertificate(key);

    var httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .sslContext(sslContext)
            .build();

    var req =
        HttpRequest.newBuilder(URI.create("https://localhost:4443"))
            .GET()
            .timeout(Duration.ofSeconds(3))
            .build();

    var res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    System.out.println(res.body());
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
