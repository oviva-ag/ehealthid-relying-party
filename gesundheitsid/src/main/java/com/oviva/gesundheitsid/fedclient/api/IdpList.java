package com.oviva.gesundheitsid.fedclient.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IdpList(
    @JsonProperty("iss") String iss,
    @JsonProperty("exp") long exp,
    @JsonProperty("iat") long iat,
    @JsonProperty("nbf") long nbf,
    @JsonProperty("idp_entity") List<IdpEntity> idpEntities) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record IdpEntity(
      @JsonProperty("iss") String iss,
      @JsonProperty("organization_name") String organizationName,
      @JsonProperty("logo_uri") String logoUri,
      @JsonProperty("user_type_supported") String userTypeSupported,
      @JsonProperty("pkv") Boolean pkv) {}
}
