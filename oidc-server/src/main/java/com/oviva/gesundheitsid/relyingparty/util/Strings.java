package com.oviva.gesundheitsid.relyingparty.util;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

public class Strings {

  public static Stream<String> mustParseCommaList(String value) {
    if (value == null || value.isBlank()) {
      return Stream.empty();
    }

    return Arrays.stream(value.split(",")).map(Strings::trimmed).filter(Objects::nonNull);
  }

  public static String trimmed(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    return value.trim();
  }
}
