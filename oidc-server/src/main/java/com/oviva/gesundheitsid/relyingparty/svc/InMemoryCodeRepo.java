package com.oviva.gesundheitsid.relyingparty.svc;

import com.oviva.gesundheitsid.relyingparty.svc.TokenIssuer.Code;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryCodeRepo implements CodeRepo {

  private final ConcurrentMap<String, Code> store = new ConcurrentHashMap<>();

  @Override
  public void save(@NonNull Code code) {
    store.put(code.code(), code);
  }

  @NonNull
  @Override
  public Optional<Code> remove(String code) {
    return Optional.ofNullable(store.remove(code));
  }
}
