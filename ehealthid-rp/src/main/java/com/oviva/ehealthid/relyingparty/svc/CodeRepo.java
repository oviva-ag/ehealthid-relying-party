package com.oviva.ehealthid.relyingparty.svc;

import com.oviva.ehealthid.relyingparty.svc.TokenIssuer.Code;
import java.util.Optional;

public interface CodeRepo {

  void save(Code code);

  Optional<Code> remove(String code);
}
