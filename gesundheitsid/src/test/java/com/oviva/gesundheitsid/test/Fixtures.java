package com.oviva.gesundheitsid.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class Fixtures {

  private Fixtures() {}

  public static String getUtf8String(String name) {
    return new String(get(name), StandardCharsets.UTF_8);
  }

  public static byte[] get(String name) {

    var path = Path.of("/fixtures", name).toString();
    try (var is = Fixtures.class.getResourceAsStream(path)) {
      if (is == null) {
        throw new IllegalStateException("path not found: '%s'".formatted(path));
      }
      return is.readAllBytes();
    } catch (IOException e) {
      throw new IllegalStateException("failed to read: '%s'".formatted(path), e);
    }
  }
}
