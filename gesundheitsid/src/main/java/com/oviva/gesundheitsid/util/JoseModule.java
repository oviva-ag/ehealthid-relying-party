package com.oviva.gesundheitsid.util;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdDelegatingSerializer;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.oviva.gesundheitsid.util.JWKSetDeserializer.JWKDeserializer;

public class JoseModule extends SimpleModule {

  public JoseModule() {
    super("jose");
    addDeserializer(JWK.class, new JWKDeserializer(JWK.class));
    addDeserializer(JWKSet.class, new JWKSetDeserializer(JWKSet.class));
    addSerializer(new StdDelegatingSerializer(JWKSet.class, new JWKSetConverter()));
  }
}
