package com.oviva.ehealthid.auth.internal.steps;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDHEncrypter;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.oviva.ehealthid.fedclient.FederationMasterClient;
import com.oviva.ehealthid.fedclient.api.EntityStatement;
import com.oviva.ehealthid.fedclient.api.EntityStatement.Metadata;
import com.oviva.ehealthid.fedclient.api.EntityStatement.OpenidProvider;
import com.oviva.ehealthid.fedclient.api.EntityStatementJWS;
import com.oviva.ehealthid.fedclient.api.OpenIdClient;
import com.oviva.ehealthid.fedclient.api.OpenIdClient.TokenResponse;
import com.oviva.ehealthid.test.ECKeyGenerator;
import java.net.URI;
import java.text.ParseException;
import org.junit.jupiter.api.Test;

class TrustedSectoralIdpStepImplTest {

  @Test
  void idpRedirectUri() {

    var redirectUri = URI.create("https://tk.example.com/auth?redirect_uri=urn:redirect:mystuff");

    var sut = new TrustedSectoralIdpStepImpl(null, null, redirectUri, null, null, null, null);

    // when & then
    assertEquals(redirectUri, sut.idpRedirectUri());
  }

  @Test
  void exchangeSectoralIdpCode() throws ParseException, JOSEException {

    var selfIssuer = URI.create("https://fachdienst.example.com");
    var callbackUri = selfIssuer.resolve("/callback");

    var fedmasterClient = mock(FederationMasterClient.class);
    var openIdClient = mock(OpenIdClient.class);
    var sectoralIdp = URI.create("https://idbroker.tk.example.com");

    var tokenEndpoint = sectoralIdp.resolve("/token");
    var redirectUri = sectoralIdp.resolve("/auth?redirect_uri=urn:redirect:mystuff");

    var sectoralIdpKey = ECKeyGenerator.generate();
    var idpEntityStatement =
        EntityStatement.create()
            .jwks(new JWKSet(sectoralIdpKey))
            .metadata(
                Metadata.create()
                    .openidProvider(
                        OpenidProvider.create().tokenEndpoint(tokenEndpoint.toString()).build())
                    .build())
            .build();
    var idpJws = new EntityStatementJWS(null, idpEntityStatement);

    var code = "12345";
    var verifier = "38988d8d8";

    var jwks = loadEncryptionKeys();

    var rawIdToken = mockIdToken(sectoralIdpKey, jwks.getKeys().get(0).toECKey());

    var res = new TokenResponse(null, 0, rawIdToken, null, null);

    when(openIdClient.exchangePkceCode(
            eq(tokenEndpoint),
            eq(code),
            eq(callbackUri.toString()),
            eq(selfIssuer.toString()),
            eq(verifier)))
        .thenReturn(res);

    var sut =
        new TrustedSectoralIdpStepImpl(
            openIdClient,
            selfIssuer,
            redirectUri,
            callbackUri,
            idpJws,
            jwks::getKeyByKeyId,
            fedmasterClient);

    when(fedmasterClient.resolveOpenIdProviderJwks(idpJws)).thenReturn(new JWKSet(sectoralIdpKey));

    // when
    var idToken = sut.exchangeSectoralIdpCode(code, verifier);

    // then
    assertEquals(
        "LWhefF_EOPv8DaFqmMuQVJA1qOfAX8zX75QU0vhVq7_niWBiBe2fRl5acPPxTNb-2kbEZaCQTI8PswuIpKJftpMPHvyCdXlGwC6qE68ag3vdIFAnsHTG1IqB7NOKgydnlA",
        idToken.body().sub());
    assertEquals(sectoralIdp.toString(), idToken.body().iss());
  }

  private String mockIdToken(ECKey signingKey, ECKey encryptionKey) throws JOSEException {

    var idTokenPayload =
        """
                        {
                          "sub": "LWhefF_EOPv8DaFqmMuQVJA1qOfAX8zX75QU0vhVq7_niWBiBe2fRl5acPPxTNb-2kbEZaCQTI8PswuIpKJftpMPHvyCdXlGwC6qE68ag3vdIFAnsHTG1IqB7NOKgydnlA",
                          "urn:telematik:claims:id": "X123447974",
                          "urn:telematik:claims:organization": "101575519",
                          "amr": [
                            "urn:telematik:auth:other"
                          ],
                          "iss": "https://idbroker.tk.example.com",
                          "nonce": "H-124356jQstUSxNixvo9w",
                          "aud": "https://ehealthid.ovivadiga.com",
                          "acr": "gematik-ehealth-loa-high",
                          "urn:telematik:claims:profession": "1.2.276.0.76.4.49",
                          "auth_time": 1729249870,
                          "exp": 1729250171,
                          "iat": 1729249871
                        }
                        """;

    var signer = new ECDSASigner(signingKey);

    var header = new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(signingKey.getKeyID()).build();
    var jws = new JWSObject(header, new Payload(idTokenPayload));
    jws.sign(signer);

    var jwe =
        new JWEObject(
            new JWEHeader.Builder(JWEAlgorithm.ECDH_ES, EncryptionMethod.A256GCM)
                .keyID(encryptionKey.getKeyID())
                .build(),
            new Payload(jws));

    jwe.encrypt(new ECDHEncrypter(encryptionKey));
    return jwe.serialize();
  }

  private JWKSet loadEncryptionKeys() throws ParseException {
    return JWKSet.parse(
        """
        {
          "keys": [
            {
              "kty": "EC",
              "d": "SLACiqrEVQXgAKOFIA8HAenlumjUtho07rhqCBruJOk",
              "use": "enc",
              "crv": "P-256",
              "kid": "relying-party-enc",
              "x": "TGY6FLnl6I4PMR4OlhMZrK8Ln_4Fs47RTBYpKSiP2kc",
              "y": "fs_HK7KbnJ7F7F3mv64lmjt2w5n_Bm3cXnRFTt-iHKU"
            }
          ]
        }
        """);
  }
}
