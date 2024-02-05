package com.oviva.gesundheitsid.relyingparty.cfg;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

public class EnvConfigProvider implements ConfigProvider {

  private final String prefix;
  private final Function<String, String> getenv;

  public EnvConfigProvider(String prefix, Function<String, String> getenv) {
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
