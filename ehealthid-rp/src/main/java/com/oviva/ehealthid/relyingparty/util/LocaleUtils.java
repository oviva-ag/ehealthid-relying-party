package com.oviva.ehealthid.relyingparty.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class LocaleUtils {

  private static final String SANITIZE_REGEX = "-([A-Za-z])+";

  private LocaleUtils() {}

  public static List<Locale> parseAcceptLanguageHeader(
      String headerValue, Collection<Locale> supportedLocales) {

    if (headerValue == null || headerValue.isBlank()) {
      throw new IllegalArgumentException("Unable to parse blank Accept-Language header");
    }

    var sanitizedHeaderValue = sanitizeLanguageRanges(headerValue);
    var languageRanges = Locale.LanguageRange.parse(sanitizedHeaderValue);
    return Locale.filter(languageRanges, supportedLocales);
  }

  private static String sanitizeLanguageRanges(String headerValue) {
    var languageList = Stream.of(headerValue.split(",")).toList();
    var sanitizedLanguageList =
        languageList.stream().map(s -> s.replaceAll(SANITIZE_REGEX, "")).toList();

    return String.join(",", new HashSet<>(sanitizedLanguageList));
  }
}
