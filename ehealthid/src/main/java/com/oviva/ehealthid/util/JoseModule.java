package com.oviva.ehealthid.util;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdDelegatingSerializer;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.oviva.ehealthid.fedclient.api.ExtendedJWKSet;
import com.oviva.ehealthid.util.JWKSetDeserializer.JWKDeserializer;

public class JoseModule extends SimpleModule {

  public JoseModule() {
    super("jose");
    addDeserializer(JWK.class, new JWKDeserializer(JWK.class));
    addDeserializer(JWKSet.class, new JWKSetDeserializer(JWKSet.class));
    addDeserializer(ExtendedJWKSet.class, new ExtendedJWKSetDeserializer());
    addSerializer(new StdDelegatingSerializer(JWKSet.class, new JWKSetConverter()));
  }
}
