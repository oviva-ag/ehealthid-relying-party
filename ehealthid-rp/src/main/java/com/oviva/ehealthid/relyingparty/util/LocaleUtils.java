package com.oviva.ehealthid.relyingparty.util;

import com.oviva.ehealthid.relyingparty.svc.ValidationException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;

public class LocaleUtils {

  public static final String BUNDLE = "i18n";
  public static final Locale DEFAULT_LOCALE = Locale.GERMANY;

  private LocaleUtils() {}

  public static List<Locale> negotiatePreferredLocales(String headerValue) {

    if (headerValue == null || headerValue.isBlank()) {
      throw new ValidationException("error.blankLangHeader");
    }

    try {
      var languageRanges = Locale.LanguageRange.parse(headerValue);
      return Locale.filter(languageRanges, getSupportedLocales(BUNDLE));
    } catch (IllegalArgumentException e) {
      throw new ValidationException("error.unparsableHeader");
    }
  }

  public static String getLocalizedErrorMessage(
      ValidationException.LocalizedErrorMessage localizedErrorMessage, Locale locale) {
    var bundle = ResourceBundle.getBundle(BUNDLE, locale);
    var localizedMessage = bundle.getString(localizedErrorMessage.messageKey());

    if (localizedErrorMessage.additionalInfo() != null
        && !localizedErrorMessage.messageKey().isBlank()) {
      localizedMessage = localizedMessage.formatted(localizedErrorMessage.additionalInfo());
    }
    return localizedMessage;
  }

  public static Set<Locale> getSupportedLocales(String baseName) {
    var locales = new HashSet<Locale>();
    var control = ResourceBundle.Control.getControl(ResourceBundle.Control.FORMAT_PROPERTIES);
    for (Locale locale : Locale.getAvailableLocales()) {
      try {
        var bundle = ResourceBundle.getBundle(baseName, locale, control);
        if (bundle.getLocale().equals(locale)) {
          locales.add(locale);
        }
      } catch (MissingResourceException e) {
        // left empty on purpose
        // Skip adding this locale
      }
    }
    return locales;
  }
}
