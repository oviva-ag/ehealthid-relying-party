package com.oviva.ehealthid.relyingparty.fed;

import java.net.URI;
import java.time.Duration;
import java.util.List;

public record FederationConfig(
    URI iss,
    URI sub,
    URI federationMaster,
    Duration ttl,
    List<String> redirectUris,
    List<String> scopes,
    String appName) {

  public static Builder create() {
    return new Builder();
  }

  public Builder builder() {
    return new Builder(iss, sub, federationMaster, ttl, redirectUris, scopes, appName);
  }

  public static final class Builder {

    private URI iss;
    private URI sub;
    private URI federationMaster;

    private Duration ttl;
    private List<String> redirectUris;
    private List<String> scopes;
    private String appName;

    private Builder() {}

    private Builder(
        URI iss,
        URI sub,
        URI federationMaster,
        Duration ttl,
        List<String> redirectUris,
        List<String> scopes,
        String appName) {
      this.iss = iss;
      this.sub = sub;
      this.federationMaster = federationMaster;
      this.ttl = ttl;
      this.redirectUris = redirectUris;
      this.scopes = scopes;
      this.appName = appName;
    }

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

    public Builder scopes(List<String> scopes) {
      this.scopes = scopes;
      return this;
    }

    public FederationConfig build() {
      return new FederationConfig(iss, sub, federationMaster, ttl, redirectUris, scopes, appName);
    }
  }
}
