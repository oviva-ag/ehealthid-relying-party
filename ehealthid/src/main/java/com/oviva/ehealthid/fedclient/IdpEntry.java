package com.oviva.ehealthid.fedclient;

import com.fasterxml.jackson.annotation.JsonProperty;

public record IdpEntry(
    @JsonProperty("iss") String iss,
    @JsonProperty("name") String name,
    @JsonProperty("logo_uri") String logoUrl) {}
