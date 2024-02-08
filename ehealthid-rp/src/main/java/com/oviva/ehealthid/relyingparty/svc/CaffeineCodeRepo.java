package com.oviva.ehealthid.relyingparty.svc;

import com.github.benmanes.caffeine.cache.Cache;
import com.oviva.ehealthid.relyingparty.svc.TokenIssuer.Code;
import java.util.Optional;

public class CaffeineCodeRepo implements CodeRepo {

  private final Cache<String, Code> store;

  public CaffeineCodeRepo(Cache<String, Code> store) {
    this.store = store;
  }

  @Override
  public void save(Code code) {
    store.put(code.code(), code);
  }

  @Override
  public Optional<Code> remove(String code) {
    return Optional.ofNullable(store.asMap().remove(code));
  }
}
