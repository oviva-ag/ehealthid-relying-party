package com.oviva.ehealthid.relyingparty.svc;

import com.oviva.ehealthid.auth.steps.SelectSectoralIdpStep;
import com.oviva.ehealthid.auth.steps.TrustedSectoralIdpStep;
import com.oviva.ehealthid.relyingparty.util.IdGenerator;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.net.URI;
import java.time.Instant;

public interface SessionRepo {

  void save(@NonNull Session session);

  Session load(@NonNull String sessionId);

  record Session(
      String id,
      Instant createdAt,
      String state,
      String nonce,
      URI redirectUri,
      String clientId,
      String codeVerifier,
      SelectSectoralIdpStep selectSectoralIdpStep,
      TrustedSectoralIdpStep trustedSectoralIdpStep) {

    public static Builder create() {
      return new Builder();
    }

    public Builder toBuilder() {
      return create()
          .id(id)
          .createdAt(createdAt)
          .state(state)
          .nonce(nonce)
          .redirectUri(redirectUri)
          .clientId(clientId)
          .codeVerifier(codeVerifier)
          .selectSectoralIdpStep(selectSectoralIdpStep)
          .trustedSectoralIdpStep(trustedSectoralIdpStep);
    }

    public static final class Builder {

      private String id;
      private Instant createdAt;
      private String state;
      private String nonce;
      private URI redirectUri;
      private String clientId;
      private String codeVerifier;
      private SelectSectoralIdpStep selectSectoralIdpStep;
      private TrustedSectoralIdpStep trustedSectoralIdpStep;

      private Builder() {}

      public Builder id(String id) {
        this.id = id;
        return this;
      }

      public Builder createdAt(Instant createdAt) {
        this.createdAt = createdAt;
        return this;
      }

      public Builder state(String state) {
        this.state = state;
        return this;
      }

      public Builder nonce(String nonce) {
        this.nonce = nonce;
        return this;
      }

      public Builder redirectUri(URI redirectUri) {
        this.redirectUri = redirectUri;
        return this;
      }

      public Builder clientId(String clientId) {
        this.clientId = clientId;
        return this;
      }

      public Builder codeVerifier(String codeVerifier) {
        this.codeVerifier = codeVerifier;
        return this;
      }

      public Builder selectSectoralIdpStep(SelectSectoralIdpStep selectSectoralIdpStep) {
        this.selectSectoralIdpStep = selectSectoralIdpStep;
        return this;
      }

      public Builder trustedSectoralIdpStep(TrustedSectoralIdpStep trustedSectoralIdpStep) {
        this.trustedSectoralIdpStep = trustedSectoralIdpStep;
        return this;
      }

      public Session build() {
        if (id == null) {
          id = IdGenerator.generateID();
        }

        if (createdAt == null) {
          createdAt = Instant.now();
        }

        return new Session(
            id,
            createdAt,
            state,
            nonce,
            redirectUri,
            clientId,
            codeVerifier,
            selectSectoralIdpStep,
            trustedSectoralIdpStep);
      }
    }
  }
}
