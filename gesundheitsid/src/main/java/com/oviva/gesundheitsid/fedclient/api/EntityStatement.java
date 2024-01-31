package com.oviva.gesundheitsid.fedclient.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nimbusds.jose.jwk.JWKSet;
import java.time.Instant;
import java.util.List;

// https://openid.net/specs/openid-federation-1_0.html#section-3
// https://wiki.gematik.de/pages/viewpage.action?pageId=523502009
@JsonIgnoreProperties(ignoreUnknown = true)
public record EntityStatement(
    // required
    @JsonProperty("iss") String iss,

    // required
    @JsonProperty("sub") String sub,

    // required, epoch seconds
    @JsonProperty("iat") long iat,

    // required, epoch seconds
    @JsonProperty("exp") long exp,

    // optional, epoch seconds
    @JsonProperty("nbf") long nbf,

    // required
    @JsonProperty("jwks") JWKSet jwks,

    // required, iss of the federation master
    @JsonProperty("authority_hints") List<String> authorityHints,

    // required
    @JsonProperty("metadata") Metadata metadata) {

  public static Builder create() {
    return new Builder();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record OpenidProvider(
      @JsonProperty("pushed_authorization_request_endpoint")
          String pushedAuthorizationRequestEndpoint,
      @JsonProperty("issuer") String issuer,
      @JsonProperty("require_pushed_authorization_requests")
          Boolean requirePushedAuthorizationRequests,
      @JsonProperty("token_endpoint") String tokenEndpoint,
      @JsonProperty("authorization_endpoint") String authorizationEndpoint,
      @JsonProperty("scopes_supported") List<String> scopesSupported,
      @JsonProperty("grant_types_supported") List<String> grantTypesSupported,
      @JsonProperty("user_type_supported") List<String> userTypeSupported) {

    public static Builder create() {
      return new Builder();
    }

    public static final class Builder {

      private String pushedAuthorizationRequestEndpoint;
      private String issuer;
      private Boolean requirePushedAuthorizationRequests;
      private String tokenEndpoint;
      private String authorizationEndpoint;
      private List<String> scopesSupported;
      private List<String> grantTypesSupported;
      private List<String> userTypeSupported;

      private Builder() {}

      public Builder pushedAuthorizationRequestEndpoint(String pushedAuthorizationRequestEndpoint) {
        this.pushedAuthorizationRequestEndpoint = pushedAuthorizationRequestEndpoint;
        return this;
      }

      public Builder issuer(String issuer) {
        this.issuer = issuer;
        return this;
      }

      public Builder requirePushedAuthorizationRequests(
          Boolean requirePushedAuthorizationRequests) {
        this.requirePushedAuthorizationRequests = requirePushedAuthorizationRequests;
        return this;
      }

      public Builder tokenEndpoint(String tokenEndpoint) {
        this.tokenEndpoint = tokenEndpoint;
        return this;
      }

      public Builder authorizationEndpoint(String authorizationEndpoint) {
        this.authorizationEndpoint = authorizationEndpoint;
        return this;
      }

      public Builder scopesSupported(List<String> scopesSupported) {
        this.scopesSupported = scopesSupported;
        return this;
      }

      public Builder grantTypesSupported(List<String> grantTypesSupported) {
        this.grantTypesSupported = grantTypesSupported;
        return this;
      }

      public Builder userTypeSupported(List<String> userTypeSupported) {
        this.userTypeSupported = userTypeSupported;
        return this;
      }

      public OpenidProvider build() {
        return new OpenidProvider(
            pushedAuthorizationRequestEndpoint,
            issuer,
            requirePushedAuthorizationRequests,
            tokenEndpoint,
            authorizationEndpoint,
            scopesSupported,
            grantTypesSupported,
            userTypeSupported);
      }
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Metadata(
      @JsonProperty("openid_provider") OpenidProvider openidProvider,

      // required
      @JsonProperty("openid_relying_party") OpenIdRelyingParty openIdRelyingParty,

      // required
      @JsonProperty("federation_entity") FederationEntity federationEntity) {

    public static Builder create() {
      return new Builder();
    }

    public static final class Builder {

      private OpenIdRelyingParty openIdRelyingParty;
      private FederationEntity federationEntity;

      private OpenidProvider openidProvider;

      private Builder() {}

      public Builder openIdRelyingParty(OpenIdRelyingParty openIdRelyingParty) {
        this.openIdRelyingParty = openIdRelyingParty;
        return this;
      }

      public Builder federationEntity(FederationEntity federationEntity) {
        this.federationEntity = federationEntity;
        return this;
      }

      public Builder openidProvider(OpenidProvider openidProvider) {
        this.openidProvider = openidProvider;
        return this;
      }

      public Metadata build() {
        return new Metadata(openidProvider, openIdRelyingParty, federationEntity);
      }
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record FederationEntity(

      // optional, redundant for claim 'client_name' in 'openid_relying_party'
      @JsonProperty("name") String name,

      // optional, for e.g. support requests
      @JsonProperty("contacts") Object contacts,

      // optional
      @JsonProperty("homepage_uri") String homepageUri,
      @JsonProperty("federation_fetch_endpoint") String federationFetchEndpoint,
      @JsonProperty("federation_list_endpoint") String federationListEndpoint,
      @JsonProperty("idp_list_endpoint") String idpListEndpoint) {

    public static Builder create() {
      return new Builder();
    }

    public static final class Builder {

      private String name;
      private String contacts;
      private String homepageUri;

      private String federationFetchEndpoint;
      private String federationListEndpoint;
      private String idpListEndpoint;

      private Builder() {}

      public Builder name(String name) {
        this.name = name;
        return this;
      }

      public Builder contacts(String contacts) {
        this.contacts = contacts;
        return this;
      }

      public Builder homepageUri(String homepageUri) {
        this.homepageUri = homepageUri;
        return this;
      }

      public Builder federationFetchEndpoint(String federationFetchEndpoint) {
        this.federationFetchEndpoint = federationFetchEndpoint;
        return this;
      }

      public Builder federationListEndpoint(String federationListEndpoint) {
        this.federationListEndpoint = federationListEndpoint;
        return this;
      }

      public Builder idpListEndpoint(String idpListEndpoint) {
        this.idpListEndpoint = idpListEndpoint;
        return this;
      }

      public FederationEntity build() {
        return new FederationEntity(
            name,
            contacts,
            homepageUri,
            federationFetchEndpoint,
            federationListEndpoint,
            idpListEndpoint);
      }
    }
  }

  // https://openid.net/specs/openid-federation-1_0.html#section-5.1.1
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record OpenIdRelyingParty(
      // optional
      @JsonProperty("organization_name") String organizationName,

      // required?, same as claim 'name' in federation identity
      @JsonProperty("client_name") String clientName,

      // required, list of allowed redirect URIs for authentication flows
      @JsonProperty("redirect_uris") List<String> redirectUris,

      // required
      @JsonProperty("response_types") List<String> responseTypes,

      // required
      @JsonProperty("client_registration_types") List<String> clientRegistrationTypes,

      // ?
      @JsonProperty("grant_types") List<String> grantTypes,

      // ?
      @JsonProperty("require_pushed_authorization_requests")
          boolean requirePushedAuthorizationRequests,

      // required, scopes our relying party wants transmitted
      @JsonProperty("scope") String scope,
      @JsonProperty("id_token_signed_response_alg") String idTokenSignedResponseAlg,
      @JsonProperty("id_token_encrypted_response_alg") String idTokenEncryptedResponseAlg,
      @JsonProperty("id_token_encrypted_response_enc") String idTokenEncryptedResponseEnc,
      @JsonProperty("jwks") JWKSet jwks) {

    public static Builder create() {
      return new Builder();
    }

    public static final class Builder {

      private String organizationName;
      private String clientName;
      private List<String> redirectUris;
      private List<String> responseTypes;
      private List<String> clientRegistrationTypes;
      private List<String> grantTypes;
      private boolean requirePushedAuthorizationRequests;
      private String scope;
      private String idTokenSignedResponseAlg;
      private String idTokenEncryptedResponseAlg;
      private String idTokenEncryptedResponseEnc;

      private JWKSet jwks;

      private Builder() {}

      public Builder organizationName(String organizationName) {
        this.organizationName = organizationName;
        return this;
      }

      public Builder clientName(String clientName) {
        this.clientName = clientName;
        return this;
      }

      public Builder redirectUris(List<String> redirectUris) {
        this.redirectUris = redirectUris;
        return this;
      }

      public Builder responseTypes(List<String> responseTypes) {
        this.responseTypes = responseTypes;
        return this;
      }

      public Builder clientRegistrationTypes(List<String> clientRegistrationTypes) {
        this.clientRegistrationTypes = clientRegistrationTypes;
        return this;
      }

      public Builder grantTypes(List<String> grantTypes) {
        this.grantTypes = grantTypes;
        return this;
      }

      public Builder requirePushedAuthorizationRequests(
          boolean requirePushedAuthorizationRequests) {
        this.requirePushedAuthorizationRequests = requirePushedAuthorizationRequests;
        return this;
      }

      public Builder scope(String scope) {
        this.scope = scope;
        return this;
      }

      public Builder idTokenSignedResponseAlg(String idTokenSignedResponseAlg) {
        this.idTokenSignedResponseAlg = idTokenSignedResponseAlg;
        return this;
      }

      public Builder idTokenEncryptedResponseAlg(String idTokenEncryptedResponseAlg) {
        this.idTokenEncryptedResponseAlg = idTokenEncryptedResponseAlg;
        return this;
      }

      public Builder idTokenEncryptedResponseEnc(String idTokenEncryptedResponseEnc) {
        this.idTokenEncryptedResponseEnc = idTokenEncryptedResponseEnc;
        return this;
      }

      public Builder jwks(JWKSet jwks) {
        this.jwks = jwks;
        return this;
      }

      public OpenIdRelyingParty build() {
        return new OpenIdRelyingParty(
            organizationName,
            clientName,
            redirectUris,
            responseTypes,
            clientRegistrationTypes,
            grantTypes,
            requirePushedAuthorizationRequests,
            scope,
            idTokenSignedResponseAlg,
            idTokenEncryptedResponseAlg,
            idTokenEncryptedResponseEnc,
            jwks);
      }
    }
  }

  public static final class Builder {

    private String iss;
    private String sub;
    private long iat;
    private long exp;

    private long nbf;
    private JWKSet jwks;
    private List<String> authorityHints;
    private Metadata metadata;

    private Builder() {}

    public Builder iss(String iss) {
      this.iss = iss;
      return this;
    }

    public Builder sub(String sub) {
      this.sub = sub;
      return this;
    }

    public Builder iat(Instant iat) {
      this.iat = iat.getEpochSecond();
      return this;
    }

    public Builder nbf(Instant nbf) {
      this.nbf = nbf.getEpochSecond();
      return this;
    }

    public Builder exp(Instant exp) {
      this.exp = exp.getEpochSecond();
      return this;
    }

    public Builder jwks(JWKSet jwks) {
      this.jwks = jwks;
      return this;
    }

    public Builder authorityHints(List<String> authorityHints) {
      this.authorityHints = authorityHints;
      return this;
    }

    public Builder metadata(Metadata metadata) {
      this.metadata = metadata;
      return this;
    }

    public EntityStatement build() {
      return new EntityStatement(iss, sub, iat, exp, nbf, jwks, authorityHints, metadata);
    }
  }
}
