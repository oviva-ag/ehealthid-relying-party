package com.oviva.ehealthid.relyingparty.util;

import static com.oviva.ehealthid.relyingparty.util.LocaleUtils.DEFAULT_LOCALE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.oviva.ehealthid.relyingparty.svc.LocalizedException.Message;
import com.oviva.ehealthid.relyingparty.svc.ValidationException;
import java.net.URI;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class LocaleUtilsTest {

  private static final URI BASE_URI = URI.create("https://idp.example.com");

  @BeforeAll
  static void setUp() {
    LocaleUtils.loadSupportedLocales();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "de-DE,en-US;q=0.5",
        "de-DE,en-US;q=0.5,en-GB;q=0.7",
        "de-DE,de,en,it;q=0.5",
        "el-GR,de-DE,en-US,en-GB"
      })
  void test_negotiatePreferredLocales(String headerValue) {
    // when
    var locales = LocaleUtils.negotiatePreferredLocales(headerValue);

    // then
    assertThat(locales.get(0).getLanguage(), is("de"));

    assertThat(locales.get(1).getLanguage(), is("en"));

    assertThat(locales.size(), is(2));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"de-DE,en-US;q=0.5", "de-DE,de,en,it;q=0.5", "el-GR,de-DE,en-US", "de-CH,it-IT"})
  void test_getNegotiatedLocale_test_getNegotiatedLocale_DefaultToGermanyLocale(
      String headerValue) {
    // when
    var locale = LocaleUtils.getNegotiatedLocale(headerValue);

    // then
    assertThat(locale, is(DEFAULT_LOCALE));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "de-DE;q=0.3,en-US;q=0.5",
        "en-US,de-DE,de,en,it;q=0.5",
        "el-GR,de-DE;q=0.7,en-US"
      })
  void test_getNegotiatedLocale_Us(String headerValue) {
    // when
    var locale = LocaleUtils.getNegotiatedLocale(headerValue);

    // then
    assertThat(locale, is(Locale.US));
  }

  @Test
  void test_mockMultipleRequestWithZeroLoadTimeForSupportedLocale() {

    var startTime = System.currentTimeMillis();
    LocaleUtils.negotiatePreferredLocales("de-DE,de,en,it;q=0.5");
    var endTime = System.currentTimeMillis();
    var executionTime = endTime - startTime;

    assertThat(executionTime, lessThanOrEqualTo(1L));

    // Mock second request
    startTime = System.currentTimeMillis();
    LocaleUtils.negotiatePreferredLocales("de-DE;q=0.8,en-US;q=0.9,de,en,it;q=0.5");
    endTime = System.currentTimeMillis();
    executionTime = endTime - startTime;
    assertThat(executionTime, lessThanOrEqualTo(1L));
  }

  @Test
  void test_negotiatePreferredLocales_validWithQualityOutOfOrder() {

    var locales = LocaleUtils.negotiatePreferredLocales("en-US;q=0.5,de-DE;q=0.8");

    // then
    assertThat(locales.get(0).getLanguage(), is("de"));
    assertThat(locales.get(1).getLanguage(), is("en"));

    // then
    assertThat(locales.size(), is(2));
  }

  @Test
  void test_negotiatePreferredLocales_noValidSizeZero() {

    var locales = LocaleUtils.negotiatePreferredLocales("el-GR;q=0.5,ja-JP;q=0.8");

    assertThat(locales.size(), is(0));
  }

  @ParameterizedTest
  @MethodSource("nullEmptyBlankSource")
  void test_negotiatePreferredLocales_nullThrowsValidationException(String headerValue) {

    var locales = LocaleUtils.negotiatePreferredLocales(headerValue);

    assertThat(locales.get(0).getLanguage(), is("de"));

    assertThat(locales.size(), is(1));
  }

  private static String[] nullEmptyBlankSource() {
    return new String[] {null, "", " "};
  }

  @Test
  void test_negotiatePreferredLocales_brokenThrowsValidationException() {

    assertThrows(ValidationException.class, () -> LocaleUtils.negotiatePreferredLocales("x21;"));
  }

  @Test
  void tes_negotiatePreferredLocales_simpleError() {

    var errorMessage = new Message("error.serverError");
    var locale = Locale.GERMANY;

    var result = LocaleUtils.formatLocalizedErrorMessage(errorMessage, locale);

    assertEquals("Ohh nein! Unerwarteter Serverfehler. Bitte versuchen Sie es erneut.", result);
  }

  @Test
  void test_negotiatePreferredLocales_errorWithContent() {

    var errorMessage = new Message("error.badRedirect", String.valueOf(BASE_URI));
    var locale = Locale.GERMANY;

    var result = LocaleUtils.formatLocalizedErrorMessage(errorMessage, locale);

    var expected =
        "Ungültige redirect_uri='%s'. Übergebener Link ist nicht gültig.".formatted(BASE_URI);
    assertEquals(expected, result);
  }
}
