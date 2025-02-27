package com.oviva.ehealthid.relyingparty.svc;

import com.github.benmanes.caffeine.cache.Expiry;

public record AfterCreatedExpiry<T>(long timeToLiveNanos) implements Expiry<String, T> {

  @Override
  public long expireAfterCreate(String key, T value, long currentTime) {
    return timeToLiveNanos;
  }

  @Override
  public long expireAfterUpdate(String key, T value, long currentTime, long currentDuration) {
    return timeToLiveNanos - currentDuration;
  }

  @Override
  public long expireAfterRead(String key, T value, long currentTime, long currentDuration) {
    return timeToLiveNanos - currentDuration;
  }
}
