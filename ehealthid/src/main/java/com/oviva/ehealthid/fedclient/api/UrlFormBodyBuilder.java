package com.oviva.ehealthid.fedclient.api;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UrlFormBodyBuilder {

  public static final String MEDIA_TYPE = "application/x-www-form-urlencoded;charset=UTF-8";
  private final List<Param> fields = new ArrayList<>();

  private UrlFormBodyBuilder() {}

  public static UrlFormBodyBuilder create() {
    return new UrlFormBodyBuilder();
  }

  public UrlFormBodyBuilder param(String name, String value) {
    fields.add(new Param(name, value));
    return this;
  }

  public byte[] build() {
    return fields.stream()
        .flatMap(this::encodeParam)
        .collect(Collectors.joining("&"))
        .getBytes(StandardCharsets.UTF_8);
  }

  private Stream<String> encodeParam(Param p) {
    if (p.name() == null || p.name().isBlank()) {
      return Stream.empty();
    }

    if (p.value() == null) {
      return Stream.of(URLEncoder.encode(p.name(), StandardCharsets.UTF_8));
    }

    return Stream.of(
        URLEncoder.encode(p.name(), StandardCharsets.UTF_8)
            + "="
            + URLEncoder.encode(p.value(), StandardCharsets.UTF_8));
  }

  private record Param(String name, String value) {}
}
