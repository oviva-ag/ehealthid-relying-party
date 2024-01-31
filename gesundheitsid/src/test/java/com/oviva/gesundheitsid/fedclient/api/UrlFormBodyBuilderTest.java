package com.oviva.gesundheitsid.fedclient.api;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class UrlFormBodyBuilderTest {

  static Stream<TC> successCases() {
    return Stream.of(
        new TC(List.of(new Param("t", "T3#&t")), "t=T3%23%26t"),
        new TC(List.of(new Param("a", "?=?"), new Param("e", "🎄")), "a=%3F%3D%3F&e=%F0%9F%8E%84"));
  }

  @ParameterizedTest
  @MethodSource("successCases")
  void field(TC tc) {

    var builder = UrlFormBodyBuilder.create();
    tc.params().forEach(p -> builder.param(p.k(), p.v()));
    var entity = builder.build();

    var encoded = new String(entity, StandardCharsets.UTF_8);
    assertEquals(tc.expected(), encoded);
  }

  record TC(List<Param> params, String expected) {}

  record Param(String k, String v) {}
}
