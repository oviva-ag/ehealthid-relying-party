package com.oviva.gesundheitsid.relyingparty.fed;

import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import java.net.URI;
import java.time.Duration;
import java.util.List;

public record FederationConfig(
    URI iss,
    URI sub,
    URI federationMaster,
    JWKSet entitySigningKeys,

    // the actual private key used for signing, _MUST_ be part of `entitySigningKeys`
    ECKey entitySigningKey,
    JWKSet relyingPartyEncKeys,
    Duration ttl,
    List<String> redirectUris,
    String appName) {

  public static Builder create() {
    return new Builder();
  }

  public static final class Builder {

    private URI iss;
    private URI sub;
    private URI federationMaster;

    private ECKey entitySigningKey;
    private JWKSet entitySigningKeys;

    private JWKSet relyingPartyEncKeys;
    private Duration ttl;
    private List<String> redirectUris;
    private String appName;

    public Builder() {}

    public Builder iss(URI iss) {
      this.iss = iss;
      return this;
    }

    public Builder sub(URI sub) {
      this.sub = sub;
      return this;
    }

    public Builder federationMaster(URI federationMaster) {
      this.federationMaster = federationMaster;
      return this;
    }

    public Builder entitySigningKey(ECKey signingKey) {
      this.entitySigningKey = signingKey;
      return this;
    }

    public Builder entitySigningKeys(JWKSet jwks) {
      this.entitySigningKeys = jwks;
      return this;
    }

    public Builder relyingPartyEncKeys(JWKSet jwks) {
      this.relyingPartyEncKeys = jwks;
      return this;
    }

    public Builder ttl(Duration ttl) {
      this.ttl = ttl;
      return this;
    }

    public Builder redirectUris(List<String> redirectUris) {
      this.redirectUris = redirectUris;
      return this;
    }

    public Builder appName(String appName) {
      this.appName = appName;
      return this;
    }

    public FederationConfig build() {
      return new FederationConfig(
          iss,
          sub,
          federationMaster,
          entitySigningKeys,
          entitySigningKey,
          relyingPartyEncKeys,
          ttl,
          redirectUris,
          appName);
    }
  }
}
