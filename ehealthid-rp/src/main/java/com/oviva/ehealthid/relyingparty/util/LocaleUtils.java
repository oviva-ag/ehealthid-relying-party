package com.oviva.ehealthid.relyingparty.util;

import com.oviva.ehealthid.relyingparty.svc.LocalizedException.Message;
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
  protected static Set<Locale> supportedLocales = loadSupportedLocales();

  private LocaleUtils() {}

  public static List<Locale> negotiatePreferredLocales(String headerValue) {

    if (headerValue == null || headerValue.isBlank()) {
      headerValue = DEFAULT_LOCALE.toLanguageTag();
    }

    try {
      var languageRanges = Locale.LanguageRange.parse(headerValue);
      return Locale.filter(languageRanges, supportedLocales);
    } catch (IllegalArgumentException e) {
      throw new ValidationException(new Message("error.unparsableHeader"));
    }
  }

  public static String formatLocalizedErrorMessage(Message localizedErrorMessage, Locale locale) {
    var bundle = ResourceBundle.getBundle(BUNDLE, locale);
    var localizedMessage = bundle.getString(localizedErrorMessage.messageKey());

    var key = localizedErrorMessage.messageKey();
    if (!key.isBlank()) {
      localizedMessage = localizedMessage.formatted((Object[]) localizedErrorMessage.args());
    }
    return localizedMessage;
  }

  public static Set<Locale> loadSupportedLocales() {
    var locales = new HashSet<Locale>();
    var control = ResourceBundle.Control.getControl(ResourceBundle.Control.FORMAT_PROPERTIES);
    for (Locale locale : Locale.getAvailableLocales()) {
      try {
        var bundle = ResourceBundle.getBundle(BUNDLE, locale, control);
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
