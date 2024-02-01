package com.oviva.gesundheitsid.relyingparty.svc;

import com.oviva.gesundheitsid.relyingparty.svc.TokenIssuer.Code;
import java.util.Optional;

public interface CodeRepo {

  void save(Code code);

  Optional<Code> remove(String code);
}
