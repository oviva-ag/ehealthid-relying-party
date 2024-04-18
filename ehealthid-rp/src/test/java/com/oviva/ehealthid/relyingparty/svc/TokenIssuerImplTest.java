package com.oviva.ehealthid.relyingparty.svc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.oviva.ehealthid.auth.IdTokenJWS;
import com.oviva.ehealthid.auth.IdTokenJWS.IdToken;
import com.oviva.ehealthid.relyingparty.svc.SessionRepo.Session;
import com.oviva.ehealthid.relyingparty.svc.TokenIssuer.Code;
import java.net.URI;
import java.text.ParseException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TokenIssuerImplTest {

  @Test
  void issueCode_unique() {
    var issuer = URI.create("https://idp.example.com");
    var keyStore = mock(KeyStore.class);
    var codeRepo = mock(CodeRepo.class);

    var sut = new TokenIssuerImpl(issuer, keyStore, codeRepo);

    var session = Session.create().build();

    // when
    var c1 = sut.issueCode(session, null);
    var c2 = sut.issueCode(session, null);

    // then
    assertNotNull(c1);
    assertNotNull(c2);

    assertNotEquals(c1.code(), c2.code());

    assertEquals(43, c1.code().length());
  }

  @Test
  void issueCode_notExpired() {
    var issuer = URI.create("https://idp.example.com");
    var keyStore = mock(KeyStore.class);
    var codeRepo = mock(CodeRepo.class);

    var sut = new TokenIssuerImpl(issuer, keyStore, codeRepo);

    var session = Session.create().build();

    // when
    var c1 = sut.issueCode(session, null);

    // then
    var now = Instant.now();
    assertTrue(c1.issuedAt().isBefore(now));
    assertTrue(c1.expiresAt().isAfter(now));
  }

  @Test
  void issueCode_propagatesValues() {
    var issuer = URI.create("https://idp.example.com");
    var keyStore = mock(KeyStore.class);
    var codeRepo = mock(CodeRepo.class);

    var sut = new TokenIssuerImpl(issuer, keyStore, codeRepo);

    var nonce = UUID.randomUUID().toString();
    var redirectUri = URI.create("https://myapp.example.com/callback");
    var clientId = "myapp";

    var session = Session.create().nonce(nonce).redirectUri(redirectUri).clientId(clientId).build();

    // when
    var code = sut.issueCode(session, null);

    // then
    assertEquals(nonce, code.nonce());
    assertEquals(redirectUri, code.redirectUri());
    assertEquals(clientId, code.clientId());
  }

  @Test
  void issueCode_saves() {
    var codeRepo = mock(CodeRepo.class);

    var sut = new TokenIssuerImpl(null, null, codeRepo);

    var nonce = UUID.randomUUID().toString();
    var redirectUri = URI.create("https://myapp.example.com/callback");
    var clientId = "myapp";

    var session = Session.create().nonce(nonce).redirectUri(redirectUri).clientId(clientId).build();

    // when
    var code = sut.issueCode(session, null);

    // then
    verify(codeRepo).save(code);
  }

  @Test
  void redeem_nonExisting() {
    var issuer = URI.create("https://idp.example.com");
    var keyStore = mock(KeyStore.class);
    var codeRepo = mock(CodeRepo.class);

    var sut = new TokenIssuerImpl(issuer, keyStore, codeRepo);

    var code = UUID.randomUUID().toString();

    var token = sut.redeem(code, null, null);

    verify(codeRepo).remove(code);
    assertNull(token);
  }

  @Test
  void redeem_twice() throws JOSEException {
    var issuer = URI.create("https://idp.example.com");

    var k = genKey();
    var keyStore = mock(KeyStore.class);

    when(keyStore.signingKey()).thenReturn(k);
    var codeRepo = mock(CodeRepo.class);

    var sut = new TokenIssuerImpl(issuer, keyStore, codeRepo);

    var redirectUri = URI.create("https://myapp.example.com");
    var clientId = "myapp";

    var federatedIdToken =
        new IdTokenJWS(
            null,
            new IdToken(
                null, "tobias", null, 0, 0, 0, null, null, null, null, null, null, null, null, null,
                null, null, null, null));

    var id = UUID.randomUUID().toString();
    var code =
        new Code(
            id, null, Instant.now().plusSeconds(10), redirectUri, null, clientId, federatedIdToken);

    when(codeRepo.remove(id)).thenReturn(Optional.of(code), Optional.empty());

    // when
    var t1 = sut.redeem(id, redirectUri.toString(), clientId);
    var t2 = sut.redeem(id, redirectUri.toString(), clientId);

    // then
    assertNotNull(t1);
    assertNull(t2);
  }

  @Test
  void redeem_idToken() throws JOSEException, ParseException {
    var issuer = URI.create("https://idp.example.com");

    var k = genKey();
    var keyStore = mock(KeyStore.class);
    when(keyStore.signingKey()).thenReturn(k);
    var codeRepo = mock(CodeRepo.class);

    var sut = new TokenIssuerImpl(issuer, keyStore, codeRepo);

    var id = UUID.randomUUID().toString();

    var nonce = UUID.randomUUID().toString();
    var redirectUri = URI.create("https://myapp.example.com/callback");
    var clientId = "myapp";

    var federatedIdToken =
        new IdTokenJWS(
            null,
            new IdToken(
                null, "tobias", null, 0, 0, 0, null, null, null, null, null, null, null, null, null,
                null, null, null, null));

    var code =
        new Code(
            id,
            null,
            Instant.now().plusSeconds(10),
            redirectUri,
            nonce,
            clientId,
            federatedIdToken);

    when(codeRepo.remove(id)).thenReturn(Optional.of(code));

    // when
    var token = sut.redeem(id, redirectUri.toString(), clientId);

    // then
    var idToken = token.idToken();

    var jws = JWSObject.parse(idToken);
    var verifier = new ECDSAVerifier(k);
    jws.verify(verifier);

    assertIdTokenClaims(jws, nonce, issuer, clientId);
  }

  @Test
  void issueIdToken_gemSpecA23035() throws JOSEException, ParseException {
    var issuer = URI.create("https://idp.example.com");

    var k = genKey();
    var keyStore = mock(KeyStore.class);
    when(keyStore.signingKey()).thenReturn(k);
    var codeRepo = mock(CodeRepo.class);

    var sut = new TokenIssuerImpl(issuer, keyStore, codeRepo);

    var id = UUID.randomUUID().toString();

    var nonce = UUID.randomUUID().toString();
    var redirectUri = URI.create("https://myapp.example.com/callback");
    var clientId = "myapp";
    var federatedSub = "S12349345";
    var federatedIss = "https://test-issuer.example.com";

    var federatedIdToken =
        new IdTokenJWS(
            null,
            new IdToken(
                federatedIss,
                federatedSub,
                null,
                0,
                0,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));

    var code =
        new Code(
            id,
            null,
            Instant.now().plusSeconds(10),
            redirectUri,
            nonce,
            clientId,
            federatedIdToken);

    when(codeRepo.remove(id)).thenReturn(Optional.of(code));

    var idToken = sut.issueIdToken(clientId, nonce, federatedIdToken);

    var jws = JWSObject.parse(idToken);
    var verifier = new ECDSAVerifier(k);
    jws.verify(verifier);

    var body = jws.getPayload().toJSONObject();
    assertEquals(federatedSub + "-" + federatedIss, body.get("sub"));
  }

  @Test
  void issueIdToken_mapsAllDefinedClaims() throws JOSEException, ParseException {
    var issuer = URI.create("https://idp.example.com");

    var k = genKey();
    var keyStore = mock(KeyStore.class);
    when(keyStore.signingKey()).thenReturn(k);
    var codeRepo = mock(CodeRepo.class);

    var sut = new TokenIssuerImpl(issuer, keyStore, codeRepo);

    var id = UUID.randomUUID().toString();

    var nonce = UUID.randomUUID().toString();
    var redirectUri = URI.create("https://myapp.example.com/callback");
    var clientId = "myapp";
    var federatedSub = "S12349345";
    var telematikBirthdate = "2020-01-01";
    var telematikAlter = "12";
    var telematikDisplayName = "Thilo";
    var telematikGivenName = "Tester";
    var telematikGeschlecht = "d";
    var telematikEmail = "t.tester@example.com";
    var telematikProfession = "1.12.234.234.1234.234";
    var telematikKvnr = "X1234";
    var telematikOrganization = "telematikOrg";

    var federatedIdToken =
        new IdTokenJWS(
            null,
            new IdToken(
                null,
                federatedSub,
                null,
                0,
                0,
                0,
                null,
                null,
                null,
                "toplevel+email@example.com",
                telematikBirthdate,
                telematikAlter,
                telematikDisplayName,
                telematikGivenName,
                telematikGeschlecht,
                telematikEmail,
                telematikProfession,
                telematikKvnr,
                telematikOrganization));

    var code =
        new Code(
            id,
            null,
            Instant.now().plusSeconds(10),
            redirectUri,
            nonce,
            clientId,
            federatedIdToken);

    when(codeRepo.remove(id)).thenReturn(Optional.of(code));

    var idToken = sut.issueIdToken(clientId, nonce, federatedIdToken);

    var jws = JWSObject.parse(idToken);
    var verifier = new ECDSAVerifier(k);
    jws.verify(verifier);

    var body = jws.getPayload().toJSONObject();

    assertEquals(telematikBirthdate, body.get("birthdate"));
    assertEquals(telematikAlter, body.get("urn:telematik:claims:alter"));
    assertEquals(telematikDisplayName, body.get("urn:telematik:claims:display_name"));
    assertEquals(telematikGivenName, body.get("urn:telematik:claims:given_name"));
    assertEquals(telematikGeschlecht, body.get("urn:telematik:claims:geschlecht"));
    assertEquals(telematikEmail, body.get("urn:telematik:claims:email"));
    assertEquals(telematikProfession, body.get("urn:telematik:claims:profession"));
    assertEquals(telematikKvnr, body.get("urn:telematik:claims:id"));
    assertEquals(telematikOrganization, body.get("urn:telematik:claims:organization"));
  }

  private void assertIdTokenClaims(JWSObject idToken, String nonce, URI issuer, String clientId) {

    var body = idToken.getPayload().toJSONObject();
    assertEquals(nonce, body.get("nonce"));
    assertEquals(issuer.toString(), body.get("iss"));
    assertEquals(clientId, body.get("aud"));
  }

  private ECKey genKey() throws JOSEException {
    var gen = new ECKeyGenerator(Curve.P_256);
    return gen.keyIDFromThumbprint(false).keyUse(KeyUse.SIGNATURE).generate();
  }
}
