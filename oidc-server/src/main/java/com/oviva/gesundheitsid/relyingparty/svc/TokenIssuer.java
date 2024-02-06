package com.oviva.gesundheitsid.relyingparty.svc;

import com.oviva.gesundheitsid.auth.IdTokenJWS;
import com.oviva.gesundheitsid.relyingparty.svc.SessionRepo.Session;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.net.URI;
import java.time.Instant;

public interface TokenIssuer {

  Code issueCode(Session session, IdTokenJWS federatedIdToken);

  Token redeem(@NonNull String code, String redirectUri, String clientId);

  record Code(
      String code,
      Instant issuedAt,
      Instant expiresAt,
      URI redirectUri,
      String nonce,
      String clientId,
      IdTokenJWS federatedIdToken) {}

  record Token(String accessToken, String idToken, long expiresInSeconds) {}
}
