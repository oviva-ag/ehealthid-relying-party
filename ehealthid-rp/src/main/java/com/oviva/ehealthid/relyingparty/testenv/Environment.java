package com.oviva.ehealthid.relyingparty.testenv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Environment {

  private static Logger logger = LoggerFactory.getLogger(Environment.class);

  // for easier mocking
  static Function<String, String> getenv = System::getenv;
  static Supplier<Path> envPath = () -> Path.of("./env.properties");

  private Environment() {}

  public static String gematikAuthHeader() {

    // for testing in TU & RU
    var name = "GEMATIK_AUTH_HEADER";
    var header = getenv.apply(name);
    if (header != null && !header.isBlank()) {
      return header;
    }

    var prop = new Properties();
    try (var br = Files.newBufferedReader(envPath.get())) {
      prop.load(br);
      return prop.getProperty(name);
    } catch (IOException e) {
      logger.atInfo().setCause(e).log("failed to load environment from properties");
    }
    return null;
  }
}
