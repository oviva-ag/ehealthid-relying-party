package com.oviva.gesundheitsid.relyingparty.svc;

import com.oviva.gesundheitsid.relyingparty.util.IdGenerator;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemorySessionRepo implements SessionRepo {

  private final ConcurrentMap<String, Session> repo = new ConcurrentHashMap<>();

  @Override
  public String save(@NonNull Session session) {
    if (session.id() != null) {
      throw new IllegalStateException(
          "session already has an ID=%s, already saved?".formatted(session.id()));
    }

    var id = IdGenerator.generateID();
    session =
        new Session(
            id, session.state(), session.nonce(), session.redirectUri(), session.clientId());

    repo.put(id, session);

    return id;
  }

  @Nullable
  @Override
  public Session load(@NonNull String sessionId) {
    return repo.get(sessionId);
  }
}
