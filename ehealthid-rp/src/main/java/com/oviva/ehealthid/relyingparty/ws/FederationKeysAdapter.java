package com.oviva.ehealthid.relyingparty.ws;

import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.oviva.ehealthid.relyingparty.fed.FederationEndpoint;
import com.oviva.ehealthid.relyingparty.providers.KeyStores;
import java.net.URI;
import java.util.ArrayList;

public class FederationKeysAdapter implements FederationEndpoint.FederationKeys {

  private final URI subject;
  private final KeyStores keyStores;

  public FederationKeysAdapter(URI subject, KeyStores keyStores) {
    this.subject = subject;
    this.keyStores = keyStores;
  }

  @Override
  public JWKSet federationKeys() {
    var keys = keyStores.federationSigJwksKeystore().keys().stream().map(k -> (JWK) k).toList();
    return new JWKSet(keys).toPublicJWKSet();
  }

  @Override
  public ECKey federationSigningKey() {
    return keyStores.federationSigJwksKeystore().keys().get(0);
  }

  @Override
  public JWKSet relyingPartyJwks() {
    var all = new ArrayList<JWK>();
    keyStores.relyingPartyEncJwksKeystore().keys().stream().map(k -> (JWK) k).forEach(all::add);
    keyStores.relyingPartySigJwksKeystore(subject).keys().stream()
        .map(k -> (JWK) k)
        .forEach(all::add);
    return new JWKSet(all).toPublicJWKSet();
  }
}
