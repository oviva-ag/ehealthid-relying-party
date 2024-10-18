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

    return jsonParser
        .getCodec()
        .readValue(node.get(fieldName).traverse(jsonParser.getCodec()), String.class);
  }

  private long parseLongField(JsonParser jsonParser, TreeNode node, String fieldName)
      throws IOException {

    return jsonParser
        .getCodec()
        .readValue(node.get(fieldName).traverse(jsonParser.getCodec()), Long.class);
  }
}
