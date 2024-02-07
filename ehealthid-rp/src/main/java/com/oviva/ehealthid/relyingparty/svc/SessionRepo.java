package com.oviva.ehealthid.relyingparty.svc;

import com.oviva.ehealthid.auth.steps.SelectSectoralIdpStep;
import com.oviva.ehealthid.auth.steps.TrustedSectoralIdpStep;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.net.URI;

public interface SessionRepo {

  void save(@NonNull Session session);

  Session load(@NonNull String sessionId);

  record Session(
      String id,
      String state,
      String nonce,
      URI redirectUri,
      String clientId,
      String codeVerifier,
      SelectSectoralIdpStep selectSectoralIdpStep,
      TrustedSectoralIdpStep trustedSectoralIdpStep) {}
}
