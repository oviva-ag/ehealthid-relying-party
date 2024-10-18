package com.oviva.ehealthid.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.nimbusds.jose.jwk.JWK;
import com.oviva.ehealthid.fedclient.api.ExtendedJWKSet;
import java.io.IOException;
import java.util.List;

public class ExtendedJWKSetDeserializer extends StdDeserializer<ExtendedJWKSet> {

  public ExtendedJWKSetDeserializer() {
    super(ExtendedJWKSet.class);
  }

  @Override
  public ExtendedJWKSet deserialize(
      JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {

    var node = jsonParser.getCodec().readTree(jsonParser);

    var keysNode = node.get("keys");
    var keys =
        jsonParser
            .getCodec()
            .readValue(keysNode.traverse(jsonParser.getCodec()), new TypeReference<List<JWK>>() {});

    var exp = parseLongField(jsonParser, node, "exp");
    var iss = parseStringField(jsonParser, node, "iss");

    return new ExtendedJWKSet(exp, iss, keys);
  }

  private String parseStringField(JsonParser jsonParser, TreeNode node, String fieldName)
      throws IOException {

    return parseField(jsonParser, node, fieldName, String.class);
  }

  private long parseLongField(JsonParser jsonParser, TreeNode node, String fieldName)
      throws IOException {

    var v = parseField(jsonParser, node, fieldName, Long.class);
    if (v == null) {
      return 0;
    }

    return v;
  }

  private <T> T parseField(JsonParser jsonParser, TreeNode node, String fieldName, Class<T> clazz)
      throws IOException {

    var n = node.get(fieldName);
    if (n == null) {
      return null;
    }

    return jsonParser.getCodec().readValue(n.traverse(jsonParser.getCodec()), clazz);
  }
}
