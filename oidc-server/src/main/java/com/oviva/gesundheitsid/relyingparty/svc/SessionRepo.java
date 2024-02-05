package com.oviva.gesundheitsid.relyingparty.svc;

import com.oviva.gesundheitsid.auth.steps.TrustedSectoralIdpStep;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.net.URI;

public interface SessionRepo {

  String save(@NonNull Session session);

  Session load(@NonNull String sessionId);

  record Session(
      String id,
      String state,
      String nonce,
      URI redirectUri,
      String clientId,
      String codeVerifier,
      TrustedSectoralIdpStep trustedSectoralIdpStep) {}
}
