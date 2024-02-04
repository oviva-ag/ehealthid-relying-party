package com.oviva.gesundheitsid.relyingparty.svc;

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
import com.oviva.gesundheitsid.relyingparty.svc.TokenIssuer.Code;
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

    var session = new SessionRepo.Session(null, null, null, null, null);

    // when
    var c1 = sut.issueCode(session);
    var c2 = sut.issueCode(session);

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

    var session = new SessionRepo.Session(null, null, null, null, null);

    // when
    var c1 = sut.issueCode(session);

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

    var session = new SessionRepo.Session(null, null, nonce, redirectUri, clientId);

    // when
    var code = sut.issueCode(session);

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

    var session = new SessionRepo.Session(null, null, nonce, redirectUri, clientId);

    // when
    var code = sut.issueCode(session);

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

    var id = UUID.randomUUID().toString();
    var code = new Code(id, null, Instant.now().plusSeconds(10), redirectUri, null, clientId);

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

    var code = new Code(id, null, Instant.now().plusSeconds(10), redirectUri, nonce, clientId);

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
