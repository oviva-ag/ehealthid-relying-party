package com.oviva.ehealthid.relyingparty.testenv;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnvironmentTest {

  @Test
  void test_envVariable() {

    var authHeader = "mySpecialS3cr3t";
    var env = Map.of("GEMATIK_AUTH_HEADER", authHeader);

    Environment.getenv = env::get;

    var got = Environment.gematikAuthHeader();

    assertEquals(authHeader, got);
  }

  @Test
  void test_envFile(@TempDir Path tempDir) throws IOException {

    var authHeader = "t0k3n";

    var f = tempDir.resolve("env.properties");
    Files.write(
        f,
        """
      GEMATIK_AUTH_HEADER=%s
      """
            .formatted(authHeader)
            .getBytes(StandardCharsets.UTF_8));

    Environment.getenv = s -> null;
    Environment.envPath = () -> f;

    var got = Environment.gematikAuthHeader();

    assertEquals(authHeader, got);
  }

  @Test
  void test_envFile_notFound(@TempDir Path tempDir) {

    var f = tempDir.resolve("does_not_exist");

    Environment.getenv = s -> null;
    Environment.envPath = () -> f;

    var got = Environment.gematikAuthHeader();

    assertNull(got);
  }
}
