package com.oviva.ehealthid.auth.internal.steps;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.jwk.JWKSet;
import com.oviva.ehealthid.fedclient.api.EntityStatement;
import com.oviva.ehealthid.fedclient.api.EntityStatement.Metadata;
import com.oviva.ehealthid.fedclient.api.EntityStatement.OpenidProvider;
import com.oviva.ehealthid.fedclient.api.EntityStatementJWS;
import com.oviva.ehealthid.fedclient.api.OpenIdClient;
import com.oviva.ehealthid.fedclient.api.OpenIdClient.TokenResponse;
import java.net.URI;
import java.text.ParseException;
import org.junit.jupiter.api.Test;

class TrustedSectoralIdpStepImplTest {

  @Test
  void idpRedirectUri() {

    var redirectUri = URI.create("https://tk.example.com/auth?redirect_uri=urn:redirect:mystuff");

    var sut = new TrustedSectoralIdpStepImpl(null, null, redirectUri, null, null, null);

    // when & then
    assertEquals(redirectUri, sut.idpRedirectUri());
  }

  @Test
  void exchangeSectoralIdpCode() throws ParseException {

    var selfIssuer = URI.create("https://fachdienst.example.com");
    var callbackUri = selfIssuer.resolve("/callback");

    var openIdClient = mock(OpenIdClient.class);
    var sectoralIdp = URI.create("https://gsi.dev.gematik.solutions");

    var tokenEndpoint = sectoralIdp.resolve("/token");
    var redirectUri = sectoralIdp.resolve("/auth?redirect_uri=urn:redirect:mystuff");

    var sectoralIdpJwks = loadSectoralIdpJwks();
    var idpEntityStatement =
        EntityStatement.create()
            .jwks(sectoralIdpJwks)
            .metadata(
                Metadata.create()
                    .openidProvider(
                        OpenidProvider.create().tokenEndpoint(tokenEndpoint.toString()).build())
                    .build())
            .build();
    var idpJws = new EntityStatementJWS(null, idpEntityStatement);

    var code = "12345";
    var verifier = "38988d8d8";

    var rawIdToken =
        "eyJhbGciOiJFQ0RILUVTIiwiZW5jIjoiQTI1NkdDTSIsImN0eSI6IkpXVCIsImtpZCI6InJlbHlpbmctcGFydHktZW5jIiwiZXBrIjp7Imt0eSI6IkVDIiwieCI6IlpuOTZnX0p3YXY0dENZNE41VEtGQUlYaXlmaTlvV2s0OUpLVEQ3aFAtTUEiLCJ5IjoiYl9YQmVZRjJ4ME5JTDhVbXBEMTVubktueFhwbHRiRzZaX3dHTGlhQjBEUSIsImNydiI6IlAtMjU2In19.._B35CBOzkZQpiYMx.witIF5OyREmxO3GEizbN7nx0yrVPQr0A2FLsOPC2oyGcNlCrsk97XnY_I13A8EXq3IMtxJIFWe2TW8dI3Cz6js8Z0Nn-qc_xJu_zcRnkjFTEtHqcxniy5nEGqLjUPFt6CypzuXf2UD-q__IMv4rJ79ODVBJ3x2ezmJworI3l-goIM6xAd-fWx6X_f2JCtxepuQNfau11pvWLmSVe2N4yod67aFxiT2mH3zLmT-bmpK8nUYsguSm8fhyFwFBOxCXDTtVDYMkZFJDbYRDewFSiskFq7wdlrtNhqkTjL83JiiYzT26B-zeQE-23p-_YjnIdC6-Wk9n-D98JIWCRGulCrsLQToWN4AeN3ShTAx8GDj3q4lbN-mXhysi43FqML4Nwb3a-Ar-qfZSQX588hv79cXhgrDTPTon6uh3_dhaIneYxTA-3iM57o5f2fnIwscMq2ra6GE2TF0WFVdkgzy9Reo-LnzoZk_3BOt6_sSMxRpc6YDfTY1abz7W1ixl_VJGqJOhAAKDcOMq0fGtcrAbG6q4fxBSdRBszUGNcjSSNQSCkohNj6aTO0lJG9XNUpEvnJgJ-lXC3VGRXq-YuCiMTIbVRt31diVg8hnncnzIDt8hSiYjghgX-mZUax4P6KymKb_czNYgyTcIGHrcoFwJgoIRJFKQnQXocr375AIUYGkSzCE1ZobrDXDsQUWFKEoKKmD5PnDZMhWVmKT42jbovhkTlqilU7nHOVIJjCtmJnK1DhYpEpEUuLli75P5HUbO3IMRSOMvgetRB8UhLFzv17j9xQE4hdzTzD1OXU96B6DD0sZB2SA_KZclkUhj4WDgTZUa_dKAUuwlnymMDyRW_AXoX05K29Oe1jwdbVAKAPlFz1RH-OKjgtbK8KmmNVRYleM6rd_inb49GlR0fRt-TK9yYqDvkZAQ_0kwVecW4Wpm1zIfF2SXZ1qZsjTBRxtL9hme1onvH84k2AtSxq09EfnIEwLKqSJTRdWQ8Q5KKNPBkPgHVUwsweG9aBOhgm5Azkuzf9SATVLE5LNiUh3cyUEwMhHwrFaf-XsCebB7dp_WL83kNXA.ziIZQ31jn9NBqfaczBTzXQ";
    var res = new TokenResponse(null, 0, rawIdToken, null, null);

    when(openIdClient.exchangePkceCode(
            eq(tokenEndpoint),
            eq(code),
            eq(callbackUri.toString()),
            eq(selfIssuer.toString()),
            eq(verifier)))
        .thenReturn(res);

    var jwks = loadEncryptionKeys();
    var sut =
        new TrustedSectoralIdpStepImpl(
            openIdClient, selfIssuer, redirectUri, callbackUri, idpJws, jwks::getKeyByKeyId);

    // when
    var idToken = sut.exchangeSectoralIdpCode(code, verifier);

    // then
    assertEquals(
        "X110411675-https://idp-test.oviva.io/auth/realms/master/ehealthid", idToken.body().sub());
    assertEquals(sectoralIdp.toString(), idToken.body().iss());
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

  private JWKSet loadSectoralIdpJwks() throws ParseException {
    return JWKSet.parse(
        """
         {
          "keys": [
            {
              "alg": "ES256",
              "crv": "P-256",
              "kid": "puk_idp_sig",
              "kty": "EC",
              "use": "sig",
              "x": "Abt2Uyrk6KhczexlBOwJOTs_eB0DsFbcNxaxa0Z0vd4",
              "y": "YZKBJtOUYEWTMknzFwBdl-6tVKyWnUDtxf2q0pST5X4"
            },
            {
              "alg": "ES256",
              "crv": "P-256",
              "kid": "puk_fed_idp_token",
              "kty": "EC",
              "use": "sig",
              "x": "YzEPFvphu4T3GgWmjPXxPT0-Pdm_Q04OLENAH98zn-M",
              "y": "AHPHggsq6YwFfW2fSIJtawMLAh9ZoKPFTZqPFgQW0t4"
            }
          ]
        }
        """);
  }
}
