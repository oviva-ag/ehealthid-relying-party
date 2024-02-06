package com.oviva.ehealthid.relyingparty.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oviva.ehealthid.relyingparty.svc.TokenIssuer.Code;
import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InMemoryCodeRepoTest {

  @Test
  void saveAndRemove() {

    var sut = new InMemoryCodeRepo();

    var id = "1234";
    var issuedAt = Instant.now();
    var expiresAt = issuedAt.plusSeconds(60);
    var redirect = URI.create("https://example.com/callback");
    var clientId = "app";

    var code = new Code(id, issuedAt, expiresAt, redirect, null, clientId, null);

    sut.save(code);

    var c1 = sut.remove(id);
    assertTrue(c1.isPresent());
    assertEquals(code, c1.get());
  }

  @Test
  void remove_nonExisting() {

    var sut = new InMemoryCodeRepo();
    var c1 = sut.remove("x");
    assertTrue(c1.isEmpty());
  }

  @Test
  void remove_twice() {

    var sut = new InMemoryCodeRepo();

    var id = "4929";

    var code = new Code(id, null, null, null, null, null, null);

    sut.save(code);

    var c1 = sut.remove(id);
    assertTrue(c1.isPresent());

    var c2 = sut.remove(id);
    assertTrue(c2.isEmpty());
  }
}
