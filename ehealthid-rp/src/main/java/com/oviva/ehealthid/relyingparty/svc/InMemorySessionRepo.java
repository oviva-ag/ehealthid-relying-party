package com.oviva.ehealthid.relyingparty.svc;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemorySessionRepo implements SessionRepo {

  private final ConcurrentMap<String, Session> repo = new ConcurrentHashMap<>();

  @Override
  public void save(@NonNull Session session) {
    if (session.id() == null) {
      throw new IllegalArgumentException("session has no ID");
    }

    repo.put(session.id(), session);
  }

  @Nullable
  @Override
  public Session load(@NonNull String sessionId) {
    return repo.get(sessionId);
  }
}
