package com.oviva.gesundheitsid.fedclient.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class InMemoryCacheImplTest {

  private static final Instant NOW = Instant.parse("2024-01-01T13:11:00.000Z");

  @Test
  void fetchesRightOne() {
    var ttl = Duration.ofSeconds(10);

    var sut = new InMemoryCacheImpl<CacheEntry>(Clock.fixed(NOW, ZoneId.of("UTC")), ttl);

    var source =
        IntStream.range(0, 10)
            .mapToObj(i -> CacheEntry.of(Integer.toString(i), NOW.plusSeconds(60)))
            .collect(Collectors.toMap(CacheEntry::value, Function.identity()));

    var rand = new Random(42L);
    for (int i = 0; i < 30; i++) {
      var key = Integer.toString(rand.nextInt(source.size()));

      // when
      var r = sut.computeIfAbsent(key, source::get);

      // then
      assertEquals(key, r.value());
    }
  }

  @Test
  void onlyFetchedOnce() {
    var clock = new MockClock();
    var ttl = Duration.ofSeconds(10);

    var sut = new InMemoryCacheImpl<CacheEntry>(clock, ttl);

    var now = Instant.parse("2024-01-01T13:11:00.000Z");
    clock.set(now);

    var key = "1";
    var e1 = CacheEntry.of(key, now.plusSeconds(60));

    var source = spy(Map.ofEntries(Map.entry(e1.value(), e1)));

    // when
    for (int i = 0; i < 4; i++) {
      var r = sut.computeIfAbsent(key, source::get);
      assertEquals(key, r.value());
    }

    // then
    verify(source, times(1)).get(key);
  }

  @Test
  void expires() {
    var clock = new MockClock();
    var ttl = Duration.ofSeconds(10);

    var sut = new InMemoryCacheImpl<CacheEntry>(clock, ttl);

    var now = Instant.parse("2024-01-01T13:11:00.000Z");
    clock.set(now);

    // do NOT use lambda, Mockito can not handle it
    var source =
        spy(
            new Function<String, CacheEntry>() {
              @Override
              public CacheEntry apply(String k) {
                return CacheEntry.of(k, clock.instant().plusSeconds(20));
              }
            });

    var key = "1";

    var N = 10;

    // when
    for (int i = 0; i < N; i++) {
      var r = sut.computeIfAbsent(key, source);
      assertEquals(key, r.value());

      clock.advanceSeconds(30);
    }

    // then
    verify(source, times(N)).apply(key);
  }

  record CacheEntry(String value, Instant exp) implements TemporalValid {

    public static CacheEntry of(String value, Instant exp) {
      return new CacheEntry(value, exp);
    }

    @Override
    public boolean isValidAt(Instant pointInTime) {
      return pointInTime.isBefore(exp);
    }
  }

  static class MockClock extends Clock {

    private Instant now;

    public void set(Instant now) {
      this.now = now;
    }

    public void advanceSeconds(long secs) {
      this.now = now.plusSeconds(secs);
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
