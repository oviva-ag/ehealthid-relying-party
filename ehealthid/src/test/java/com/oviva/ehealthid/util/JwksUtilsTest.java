package com.oviva.ehealthid.util;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class JwksUtilsTest {

  @Test
  void load() {
    var jwks = JwksUtils.load(Path.of("./src/test/resources/fixtures/jwks_utils_sample.json"));

    assertEquals(1, jwks.size());
    assertNotNull(jwks.getKeyByKeyId("test"));
  }
}
