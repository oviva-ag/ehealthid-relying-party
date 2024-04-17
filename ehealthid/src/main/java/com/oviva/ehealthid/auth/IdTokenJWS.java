package com.oviva.ehealthid.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nimbusds.jose.JWSObject;

public record IdTokenJWS(JWSObject jws, IdToken body) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record IdToken(
      @JsonProperty("iss") String iss,
      @JsonProperty("sub") String sub,
      @JsonProperty("aud") String aud,
      @JsonProperty("iat") long iat,
      @JsonProperty("exp") long exp,
      @JsonProperty("nbf") long nbf,
      @JsonProperty("nonce") String nonce,
      @JsonProperty("acr") String acr,
      @JsonProperty("amr") String amr,
      @JsonProperty("email") String email,

      // The gemspec says that the claim is "birthdate", but
      // all other claims are prefixed with "urn:telematik:claims:".
      // I decided to implement to spec but we will likely need to adapt this.
      @JsonProperty("birthdate") String telematikBirthdate,
      @JsonProperty("urn:telematik:claims:alter") String telematikAlter,
      @JsonProperty("urn:telematik:claims:display_name") String telematikDisplayName,
      @JsonProperty("urn:telematik:claims:given_name") String telematikGivenName,
      @JsonProperty("urn:telematik:claims:geschlecht") String telematikGeschlecht,
      @JsonProperty("urn:telematik:claims:email") String telematikEmail,
      @JsonProperty("urn:telematik:claims:profession") String telematikProfession,

      // for insured person (IP) the immutable part of the Krankenversichertennummer (KVNR)
      @JsonProperty("urn:telematik:claims:id") String telematikKvnr,
      @JsonProperty("urn:telematik:claims:organization") String telematikOrganization) {}
}
