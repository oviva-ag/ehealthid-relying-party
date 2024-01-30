package com.oviva.gesundheitsid.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Environment {

  private static Logger logger = LoggerFactory.getLogger(Environment.class);

  public static String gematikAuthHeader() {

    // for testing in TU
    var name = "GEMATIK_AUTH_HEADER";
    var header = System.getenv(name);
    if (header != null && !header.isBlank()) {
      return header;
    }

    var prop = new Properties();
    try (var br = Files.newBufferedReader(Path.of("./env.properties"))) {
      prop.load(br);
      return prop.getProperty(name);
    } catch (IOException e) {
      logger.atInfo().setCause(e).log("failed to load environment from properties");
    }
    return null;
  }
}
