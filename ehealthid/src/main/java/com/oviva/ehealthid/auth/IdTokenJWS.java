package com.oviva.ehealthid.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nimbusds.jose.JWSObject;
import java.util.List;

public record IdTokenJWS(JWSObject jws, IdToken body) {

  // https://openid.net/specs/openid-connect-core-1_0.html#IDToken
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

      // A_23129-03
      // https://gemspec.gematik.de/docs/gemSpec/gemSpec_IDP_Sek/latest/#A_23129-03
      @JsonProperty("amr") List<String> amr,
      @JsonProperty("email") String email,

      // telematik claims according to
      // https://gemspec.gematik.de/docs/gemSpec/gemSpec_IDP_Sek/latest/#A_22989
      @JsonProperty("birthdate") String telematikBirthdate,
      @JsonProperty("urn:telematik:claims:alter") String telematikAge,
      @JsonProperty("urn:telematik:claims:display_name") String telematikDisplayName,
      @JsonProperty("urn:telematik:claims:given_name") String telematikGivenName,
      @JsonProperty("urn:telematik:claims:geschlecht") String telematikGender,
      @JsonProperty("urn:telematik:claims:email") String telematikEmail,
      @JsonProperty("urn:telematik:claims:profession") String telematikProfession,

      // for insured person (IP) the immutable part of the Krankenversichertennummer (KVNR)
      @JsonProperty("urn:telematik:claims:id") String telematikKvnr,
      @JsonProperty("urn:telematik:claims:organization") String telematikOrganization) {}
}
