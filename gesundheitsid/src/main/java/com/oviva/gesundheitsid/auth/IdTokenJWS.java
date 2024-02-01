package com.oviva.gesundheitsid.auth;

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
      @JsonProperty("urn:telematik:claims:profession") String telematikProfession,
      @JsonProperty("urn:telematik:claims:given_name") String telematikGivenName,

      // for insured person (IP) the immutable part of the Krankenversichertennummer (KVNR)
      @JsonProperty("urn:telematik:claims:id") String telematikKvnr,
      @JsonProperty("urn:telematik:claims:email") String telematikEmail) {}
}
