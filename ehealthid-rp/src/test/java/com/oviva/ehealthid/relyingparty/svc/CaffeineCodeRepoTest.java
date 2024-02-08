package com.oviva.ehealthid.relyingparty.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.oviva.ehealthid.relyingparty.svc.TokenIssuer.Code;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CaffeineCodeRepoTest {

  @Test
  void saveAndRemove() {

    Cache<String, Code> cache = Caffeine.newBuilder().build();

    var sut = new CaffeineCodeRepo(cache);

    var id = "1234";
    var issuedAt = Instant.now();
    var expiresAt = issuedAt.plusSeconds(60);
    var redirect = URI.create("https://example.com/callback");
    var clientId = "app";

    var code = new Code(id, issuedAt, expiresAt, redirect, null, clientId, null);

    // when
    sut.save(code);
    var c1 = sut.remove(id);

    // then
    assertTrue(c1.isPresent());
    assertEquals(code, c1.get());
  }

  @Test
  void remove_nonExisting() {

    Cache<String, Code> cache = Caffeine.newBuilder().build();

    var sut = new CaffeineCodeRepo(cache);

    // when
    var c1 = sut.remove("x");

    // then
    assertTrue(c1.isEmpty());
  }

  @Test
  void remove_twice() {

    Cache<String, Code> cache = Caffeine.newBuilder().build();
    var sut = new CaffeineCodeRepo(cache);

    var id = "4929";

    var code = new Code(id, null, null, null, null, null, null);

    sut.save(code);

    // when & then
    var c1 = sut.remove(id);
    assertTrue(c1.isPresent());

    var c2 = sut.remove(id);
    assertTrue(c2.isEmpty());
  }

  @Test
  void bounded() {

    var maxSize = 10;
    Cache<String, Code> cache = Caffeine.newBuilder().maximumSize(maxSize).build();

    var sut = new CaffeineCodeRepo(cache);

    var ids = IntStream.range(0, 100).mapToObj(Integer::toString).toList();

    var redirect = URI.create("https://example.com/callback");
    var clientId = "app";
    ids.forEach(
        id -> {
          var issuedAt = Instant.now();
          var expiresAt = issuedAt.plusSeconds(60);

          var code = new Code(id, issuedAt, expiresAt, redirect, null, clientId, null);
          sut.save(code);
        });

    cache.cleanUp();

    // when
    var remainingCount = ids.stream().map(sut::remove).flatMap(Optional::stream).count();

    // then
    assertEquals(maxSize, remainingCount);
  }
}
