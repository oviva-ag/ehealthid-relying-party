package com.oviva.gesundheitsid.fedclient.api;

import java.util.function.Function;

public interface Cache<T extends TemporalValid> {

  T computeIfAbsent(String key, Function<String, T> supplier);
}
