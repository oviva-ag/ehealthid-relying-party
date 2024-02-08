package com.oviva.ehealthid.relyingparty.svc;

import com.github.benmanes.caffeine.cache.Cache;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.time.Instant;

public class CaffeineSessionRepo implements SessionRepo {

  private final Cache<String, Session> store;
  private final Duration timeToLive;

  public CaffeineSessionRepo(Cache<String, Session> cache, Duration timeToLive) {
    this.store = cache;
    this.timeToLive = timeToLive;
  }

  @Override
  public void save(@NonNull Session session) {
    if (session.id() == null) {
      throw new IllegalArgumentException("session has no ID");
    }

    store.put(session.id(), session);
  }

  @Nullable
  @Override
  public Session load(@NonNull String sessionId) {
    var session = store.getIfPresent(sessionId);
    if (session == null || session.createdAt().plus(timeToLive).isBefore(Instant.now())) {
      return null;
    }
    return session;
  }

  @Nullable
  @Override
  public Session remove(@NonNull String sessionId) {
    var session = store.asMap().remove(sessionId);
    if (session == null || session.createdAt().plus(timeToLive).isBefore(Instant.now())) {
      return null;
    }
    return session;
  }
}
