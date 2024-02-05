package com.oviva.gesundheitsid.relyingparty.svc;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemorySessionRepoTest {

  @Test
  void save() {
    var sut = new InMemorySessionRepo();
    var session = new SessionRepo.Session(null, null, null, null, null);

    var id1 = sut.save(session);
    assertNotNull(id1);

    var id2 = sut.save(session);
    assertNotNull(id1);

    assertNotEquals(id1, id2);
  }

  @Test
  void save_alreadySaved() {
    var sut = new InMemorySessionRepo();
    var session = new SessionRepo.Session("1", null, null, null, null);

    assertThrows(IllegalStateException.class, () -> sut.save(session));
  }

  @Test
  void load() {

    var sut = new InMemorySessionRepo();

    var state = "myState";
    var nonce = UUID.randomUUID().toString();
    var redirectUri = URI.create("https://example.com/callback");
    var clientId = "app";

    var session = new SessionRepo.Session(null, state, nonce, redirectUri, clientId);

    var id = sut.save(session);

    var got = sut.load(id);

    assertEquals(id, got.id());
    assertEquals(state, got.state());
    assertEquals(redirectUri, got.redirectUri());
    assertEquals(clientId, got.clientId());
  }
}
