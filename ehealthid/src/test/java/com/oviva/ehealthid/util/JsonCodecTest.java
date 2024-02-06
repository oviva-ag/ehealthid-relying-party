package com.oviva.ehealthid.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.oviva.ehealthid.test.Fixtures;
import com.oviva.ehealthid.util.JsonCodec.DeserializeException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class JsonCodecTest {

  static List<EncodeTC> testCasesEncode() throws ParseException {
    return List.of(
        new EncodeTC(List.of("a", "b", "c"), "[\"a\",\"b\",\"c\"]"),
        new EncodeTC(
            JWKSet.parse(Fixtures.getUtf8String("json_codec_jwks.json")),
            """
                {"keys":[{"kty":"EC","d":"6RrmHekWp_RwY6FNlM46zwt1wFytfVQSYrS2-DDLj7g","use":"enc","crv":"P-256","kid":"test","x":"M4yMVgv6nV9AHvNCdrFUZ2zLnSD8yXFZBgbLgXU0vAc","y":"AvE4diGs4teOYHECACyi41UMxPGv8myq-Y7MBZGfwzY"}]}"""));
  }

  @Test
  void readBadJson() {
    var raw = """
    {"a": bad }
    """;
    assertThrows(DeserializeException.class, () -> JsonCodec.readValue(raw, Object.class));
  }

  @Test
  void readJwksString() {
    var raw = Fixtures.getUtf8String("json_codec_jwks.json");
    var jwks = JsonCodec.readValue(raw, JWKSet.class);

    assertThat(jwks.getKeys(), hasSize(1));
    var key = jwks.getKeys().get(0);
    assertEquals("test", key.getKeyID());
    assertEquals("EC", key.getKeyType().getValue());
  }

  @Test
  void readJwksBytes() {
    var raw = Fixtures.getUtf8String("json_codec_jwks.json").getBytes(StandardCharsets.UTF_8);
    var jwks = JsonCodec.readValue(raw, JWKSet.class);

    assertThat(jwks.getKeys(), hasSize(1));
    var key = jwks.getKeys().get(0);
    assertEquals("test", key.getKeyID());
    assertEquals("EC", key.getKeyType().getValue());
  }

  @ParameterizedTest
  @MethodSource("testCasesEncode")
  void encode(EncodeTC tc) throws JsonProcessingException {

    var encoded = JsonCodec.writeValueAsString(tc.in());

    var om = new ObjectMapper();
    var expected = om.readTree(tc.expected());
    var got = om.readTree(encoded);

    assertEquals(expected, got);
  }

  record EncodeTC(Object in, String expected) {}
}
