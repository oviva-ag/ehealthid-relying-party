package com.oviva.ehealthid.relyingparty.svc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.oviva.ehealthid.relyingparty.svc.SessionRepo.Session;
import com.oviva.ehealthid.relyingparty.util.IdGenerator;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CaffeineSessionRepoTest {

  @Test
  void save_noId() {
    var sut = new CaffeineSessionRepo(null, Duration.ofMinutes(5));
    var session = new Session(null, null, null, null, null, null, null, null, null, null);

    assertThrows(IllegalArgumentException.class, () -> sut.save(session));
  }

  @Test
  void load_mockCache() {

    var cache = mock(Cache.class);
    var sut = new CaffeineSessionRepo(cache, Duration.ofMinutes(5));

    var id = "mySessionId";
    var state = "myState";
    var nonce = UUID.randomUUID().toString();
    var redirectUri = URI.create("https://example.com/callback");
    var appUri = URI.create("https://example.com/app");
    var clientId = "app";

    var session =
        Session.create()
            .id(id)
            .state(state)
            .nonce(nonce)
            .redirectUri(redirectUri)
            .appUri(appUri)
            .clientId(clientId)
            .build();

    when(cache.getIfPresent(id)).thenReturn(session);

    sut.save(session);

    // when
    var got = sut.load(id);

    // then
    assertNotNull(got);
    assertEquals(id, got.id());
    assertEquals(state, got.state());
    assertEquals(redirectUri, got.redirectUri());
    assertEquals(appUri, got.appUri());
    assertEquals(clientId, got.clientId());

    verify(cache).put(id, session);
  }

  @Test
  void load_realCache() {

    var ttl = Duration.ofMinutes(5);
    Cache<String, Session> cache =
        Caffeine.newBuilder()
            .expireAfter(new AfterCreatedExpiry(ttl.toNanos()))
            .maximumSize(1000)
            .build();
    var sut = new CaffeineSessionRepo(cache, ttl);

    var state = "myState";
    var nonce = UUID.randomUUID().toString();
    var redirectUri = URI.create("https://example.com/callback");
    var appUri = URI.create("https://example.com/app");
    var clientId = "app";

    var sesionIds = IntStream.range(0, 100).mapToObj(Integer::toString).toList();

    sesionIds.stream()
        .map(
            i ->
                Session.create()
                    .id(i)
                    .state(state)
                    .nonce(nonce)
                    .redirectUri(redirectUri)
                    .appUri(appUri)
                    .clientId(clientId)
                    .build())
        .forEach(sut::save);

    sesionIds.forEach(
        id -> {

          // when
          var got = sut.load(id);

          // then
          assertNotNull(got);
          assertEquals(id, got.id());
          assertEquals(state, got.state());
          assertEquals(redirectUri, got.redirectUri());
          assertEquals(appUri, got.appUri());
          assertEquals(clientId, got.clientId());
        });
  }

  @Test
  void remove() {

    var ttl = Duration.ofMinutes(5);
    Cache<String, Session> cache =
        Caffeine.newBuilder()
            .expireAfter(new AfterCreatedExpiry(ttl.toNanos()))
            .maximumSize(1000)
            .build();
    var sut = new CaffeineSessionRepo(cache, ttl);

    var state = "myState";
    var nonce = UUID.randomUUID().toString();
    var redirectUri = URI.create("https://example.com/callback");
    var appUri = URI.create("https://example.com/app");
    var clientId = "app";

    var id = IdGenerator.generateID();

    var session =
        Session.create()
            .id(id)
            .state(state)
            .nonce(nonce)
            .redirectUri(redirectUri)
            .appUri(appUri)
            .clientId(clientId)
            .build();
    sut.save(session);

    // when
    var got1 = sut.remove(id);
    var got2 = sut.remove(id);

    // then
    assertNotNull(got1);
    assertEquals(id, got1.id());
    assertEquals(state, got1.state());
    assertEquals(redirectUri, got1.redirectUri());
    assertEquals(appUri, got1.appUri());
    assertEquals(clientId, got1.clientId());

    assertNull(got2);
  }

  @Test
  void load_realCache_bounded() {

    var maxSize = 10;
    var ttl = Duration.ofMinutes(5);
    Cache<String, Session> store = Caffeine.newBuilder().maximumSize(maxSize).build();
    var sut = new CaffeineSessionRepo(store, ttl);

    var state = "myState";
    var nonce = UUID.randomUUID().toString();
    var redirectUri = URI.create("https://example.com/callback");
    var appUri = URI.create("https://example.com/app");
    var clientId = "app";

    var sesionIds = IntStream.range(0, 100).mapToObj(Integer::toString).toList();

    sesionIds.stream()
        .map(
            i ->
                Session.create()
                    .id(i)
                    .state(state)
                    .nonce(nonce)
                    .redirectUri(redirectUri)
                    .appUri(appUri)
                    .clientId(clientId)
                    .build())
        .forEach(sut::save);

    store.cleanUp();

    // when
    var remainingCount =
        sesionIds.stream().flatMap(id -> Optional.ofNullable(sut.load(id)).stream()).count();

    // then
    assertEquals(maxSize, remainingCount);
  }
}
