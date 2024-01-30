package com.oviva.gesundheitsid.fedclient.api;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class InMemoryCacheImpl<T extends TemporalValid> implements Cache<T> {

  private final Map<String, T> cache = new ConcurrentHashMap<>();

  private final Clock clock;
  private final Duration minTimeToLive;

  public InMemoryCacheImpl(Clock clock, Duration minTimeToLive) {
    this.clock = clock;
    this.minTimeToLive = minTimeToLive;
  }

  @Override
  public T computeIfAbsent(String key, Function<String, T> supplier) {
    return cache.compute(
        key,
        (String k, T current) -> {
          if (isValidLongEnough(current)) {
            return current;
          }
          return supplier.apply(key);
        });
  }

  private boolean isValidLongEnough(T entity) {
    if (entity == null) {
      return false;
    }

    if (!entity.isValidAt(clock.instant())) {
      return false;
    }

    var future = clock.instant().plus(minTimeToLive);
    return entity.isValidAt(future);
  }
}
