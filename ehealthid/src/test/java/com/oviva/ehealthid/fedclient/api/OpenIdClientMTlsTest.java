package com.oviva.ehealthid.fedclient.api;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.admin.model.ServeEventQuery;
import com.github.tomakehurst.wiremock.http.Body;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.oauth2.sdk.id.Issuer;
import com.nimbusds.oauth2.sdk.util.X509CertificateUtils;
import com.oviva.ehealthid.util.TlsContext;
import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.net.ssl.*;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class OpenIdClientMTlsTest {

  private static final URI ISSUER = URI.create("https://example.com");
  private static final ECKey SIGNING_KEY = generateSigningKey(ISSUER);

  private static final String PAR_REDIRECT = "https://par.example.com/auth";
  private static final String PAR_RESPONSE =
      """
  { "request_uri":"%s", "expires_in": 1234 }
  """.formatted(PAR_REDIRECT);

  @RegisterExtension
  static WireMockExtension wmServer =
      WireMockExtension.newInstance()
          .options(
              wireMockConfig()
                  .httpDisabled(true)
                  .dynamicHttpsPort()
                  .needClientAuth(true)
                  .trustStorePath(createTrustManager(SIGNING_KEY).toString()))
          .build();

  @Test
  void requestPushedUri_mTls() throws Exception {

    // given
    var httpClient = newHttpClient();
    var clientUnderTest = new OpenIdClient(httpClient);

    var path = "/par";

    var parUri = URI.create(wmServer.getRuntimeInfo().getHttpsBaseUrl()).resolve(path);

    var stub =
        wmServer.stubFor(
            post(path)
                .willReturn(
                    created()
                        .withResponseBody(
                            Body.fromJsonBytes(PAR_RESPONSE.getBytes(StandardCharsets.UTF_8)))));

    var parBody = ParBodyBuilder.create();

    // when
    var res = clientUnderTest.requestPushedUri(parUri, parBody);

    // then
    var serveEvents =
        wmServer.getServeEvents(ServeEventQuery.forStubMapping(stub)).getServeEvents();

    var req = serveEvents.get(0);
    assertEquals("https", req.getRequest().getScheme());
    assertEquals(PAR_REDIRECT, res.requestUri());
  }

  private com.oviva.ehealthid.fedclient.api.HttpClient newHttpClient()
      throws NoSuchAlgorithmException, JOSEException, KeyManagementException {

    // mTLS for OIDC
    // https://connect2id.com/products/nimbus-oauth-openid-connect-sdk/examples/utils/custom-key-store

    var sslContext = SSLContext.getInstance("TLS");

    sslContext.init(
        TlsContext.keyManagerOf(
            SIGNING_KEY.getParsedX509CertChain().get(0), SIGNING_KEY.toPrivateKey()),
        new TrustManager[] {new NaiveTrustManager()},
        null);

    var javaHttpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .sslContext(sslContext)
            .build();

    return new JavaHttpClient(javaHttpClient);
  }

  private static ECKey generateSigningKey(URI issuer) {

    try {
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

      key = new ECKey.Builder(key).x509CertChain(List.of(Base64.encode(cert.getEncoded()))).build();

      return key;
    } catch (CertificateEncodingException
        | IOException
        | OperatorCreationException
        | JOSEException e) {
      throw new RuntimeException("failed to generate EC key", e);
    }
  }

  private static Path createTrustManager(JWK key) {

    var ksType = KeyStore.getDefaultType();
    var password = "password".toCharArray();
    try {
      var path = Files.createTempFile("mtls_truststore", ksType);
      try (var fout =
          Files.newOutputStream(
              path, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {

        var ks = KeyStore.getInstance(ksType);
        ks.load(null, password);

        for (X509Certificate cert : key.getParsedX509CertChain()) {
          ks.setCertificateEntry(ISSUER.toString(), cert);
        }

        ks.store(fout, password);

        return path;
      } catch (CertificateException | KeyStoreException | NoSuchAlgorithmException e) {
        throw new RuntimeException(e);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  static class NaiveTrustManager extends X509ExtendedTrustManager {
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

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType)
        throws CertificateException {}

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType)
        throws CertificateException {}

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
    }
  }
}
