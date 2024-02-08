package com.oviva.ehealthid.relyingparty.svc;

import com.github.benmanes.caffeine.cache.Expiry;
import org.checkerframework.checker.index.qual.NonNegative;

public record AfterCreatedExpiry<T>(long timeToLiveNanos) implements Expiry<String, T> {

  @Override
  public long expireAfterCreate(String key, T value, long currentTime) {
    return timeToLiveNanos;
  }

  @Override
  public long expireAfterUpdate(
      String key, T value, long currentTime, @NonNegative long currentDuration) {
    return timeToLiveNanos - currentDuration;
  }

  @Override
  public long expireAfterRead(
      String key, T value, long currentTime, @NonNegative long currentDuration) {
    return timeToLiveNanos - currentDuration;
  }
}
