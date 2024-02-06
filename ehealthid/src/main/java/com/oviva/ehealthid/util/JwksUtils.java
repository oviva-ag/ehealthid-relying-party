package com.oviva.ehealthid.util;

import com.nimbusds.jose.jwk.JWKSet;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;

public class JwksUtils {

  private JwksUtils() {}

  public static JWKSet load(Path path) {

    try (var fin = Files.newInputStream(path)) {
      return JWKSet.load(fin);
    } catch (IOException | ParseException e) {
      var fullPath = path.toAbsolutePath();
      throw new RuntimeException(
          "failed to load JWKS from '%s' ('%s')".formatted(path, fullPath), e);
    }
  }
}
