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
    String appName,
    String organizationName) {

  public static Builder create() {
    return new Builder();
  }

  public Builder builder() {
    return new Builder(
        iss, sub, federationMaster, ttl, redirectUris, scopes, appName, organizationName);
  }

  public static final class Builder {

    private URI iss;
    private URI sub;
    private URI federationMaster;

    private Duration ttl;
    private List<String> redirectUris;
    private List<String> scopes;
    private String appName;
    private String organizationName;

    private Builder() {}

    private Builder(
        URI iss,
        URI sub,
        URI federationMaster,
        Duration ttl,
        List<String> redirectUris,
        List<String> scopes,
        String appName,
        String organizationName) {
      this.iss = iss;
      this.sub = sub;
      this.federationMaster = federationMaster;
      this.ttl = ttl;
      this.redirectUris = redirectUris;
      this.scopes = scopes;
      this.appName = appName;
      this.organizationName = organizationName;
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

    public Builder organizationName(String organizationName) {
      this.organizationName = organizationName;
      return this;
    }

    public FederationConfig build() {
      return new FederationConfig(
          iss, sub, federationMaster, ttl, redirectUris, scopes, appName, organizationName);
    }
  }
}
