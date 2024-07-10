package com.oviva.ehealthid.relyingparty.cfg;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public class EnvConfigProvider implements ConfigProvider {

  private final String prefix;
  private final Function<String, String> getenv;

  public EnvConfigProvider(String prefix, UnaryOperator<String> getenv) {
    this.prefix = prefix;
    this.getenv = getenv;
  }

  @Override
  public Optional<String> get(String name) {

    var mangled = prefix + "_" + name;
    mangled = mangled.toUpperCase(Locale.ROOT);
    mangled = mangled.replaceAll("[^A-Z0-9]", "_");

    return Optional.ofNullable(getenv.apply(mangled));
  }
}
