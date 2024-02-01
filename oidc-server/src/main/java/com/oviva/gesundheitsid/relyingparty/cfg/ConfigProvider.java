package com.oviva.gesundheitsid.relyingparty.cfg;

import java.util.Optional;

public interface ConfigProvider {
  Optional<String> get(String name);
}
