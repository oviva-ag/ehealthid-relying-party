package com.oviva.ehealthid.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.oviva.ehealthid.test.Fixtures;
import com.oviva.ehealthid.util.JsonCodec.DeserializeException;
import com.oviva.ehealthid.util.JsonCodec.SerializeException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
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

  static List<FailingEncodeTC> testCasesFailingEncode() {

    var loop = new ArrayList<>();
    var b = new ArrayList<>();
    loop.add(b);
    b.add(loop);

    return List.of(
        new FailingEncodeTC(loop, SerializeException.class),
        new FailingEncodeTC(new JsonCodecTest(), SerializeException.class));
  }

  @Test
  void testDebug_includeSource() {

    var raw = """
    {"hello": "world" }\
    """;
    var bytes = raw.getBytes(StandardCharsets.UTF_8);

    // when
    var e =
        assertThrows(
            DeserializeException.class, () -> JsonCodec.readValue(bytes, HelloWorld.class));

    // then
    var msg = allMessages(e);
    assertTrue(msg.contains(raw));
  }

  private String allMessages(Throwable e) {
    var buf = new StringBuilder();
    while (e != null) {
      buf.append(e.getMessage()).append('\n');
      e = e.getCause();
    }
    return buf.toString();
  }

  private record HelloWorld(List<String> hello) {}

  @Test
  void readBadJson() {
    var raw = """
    {"a": bad }
    """.getBytes(StandardCharsets.UTF_8);
    assertThrows(DeserializeException.class, () -> JsonCodec.readValue(raw, Object.class));
  }

  @Test
  void readJwksString() {
    var raw = Fixtures.get("json_codec_jwks.json");
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
  @MethodSource("testCasesFailingEncode")
  void encode(FailingEncodeTC tc) {

    var exception = assertThrows(Exception.class, () -> JsonCodec.writeValueAsString(tc.in()));
    assertInstanceOf(tc.expectedException(), exception);
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

  record FailingEncodeTC(Object in, Class<? extends Throwable> expectedException) {}
}
