package com.oviva.gesundheitsid.relyingparty.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class StringsTest {

  static Stream<ListTC> listTestCases() {
    return Stream.of(
        new ListTC("a,b,c", List.of("a", "b", "c")),
        new ListTC("a,,c", List.of("a", "c")),
        new ListTC("a,  ,c", List.of("a", "c")),
        new ListTC(",a,,,c,", List.of("a", "c")),
        new ListTC(null, List.of()),
        new ListTC("a , b ,  \tc", List.of("a", "b", "c")));
  }

  @ParameterizedTest
  @MethodSource("listTestCases")
  void mustParseCommaList(ListTC t) {
    var got = Strings.mustParseCommaList(t.value());
    assertEquals(t.expected(), got.toList());
  }

  record ListTC(String value, List<String> expected) {}
}
