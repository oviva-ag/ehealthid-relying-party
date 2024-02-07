package com.oviva.ehealthid.relyingparty.svc;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemorySessionRepoTest {

  @Test
  void save_noId() {
    var sut = new InMemorySessionRepo();
    var session = new SessionRepo.Session(null, null, null, null, null, null, null, null);

    assertThrows(IllegalArgumentException.class, () -> sut.save(session));
  }

  @Test
  void load() {

    var sut = new InMemorySessionRepo();

    var id = "mySessionId";
    var state = "myState";
    var nonce = UUID.randomUUID().toString();
    var redirectUri = URI.create("https://example.com/callback");
    var clientId = "app";

    var session =
        new SessionRepo.Session(id, state, nonce, redirectUri, clientId, null, null, null);

    sut.save(session);

    var got = sut.load(id);

    assertEquals(id, got.id());
    assertEquals(state, got.state());
    assertEquals(redirectUri, got.redirectUri());
    assertEquals(clientId, got.clientId());
  }
}
