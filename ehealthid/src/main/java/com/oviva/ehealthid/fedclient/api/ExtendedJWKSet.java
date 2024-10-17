package com.oviva.ehealthid.fedclient.api;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import java.util.List;

// slight variation of a JWKSet :/
// https://openid.net/specs/openid-connect-federation-1_0-21.html#name-openid-connect-and-oauth2-m
public record ExtendedJWKSet(long exp, String iss, List<JWK> keys) {

  public JWKSet toJWKSet() {
    if (keys == null) {
      return new JWKSet();
    }
    return new JWKSet(keys);
  }
}
