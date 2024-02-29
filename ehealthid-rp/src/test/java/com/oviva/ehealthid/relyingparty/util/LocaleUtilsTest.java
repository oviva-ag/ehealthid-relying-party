package com.oviva.ehealthid.relyingparty.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

import com.oviva.ehealthid.relyingparty.svc.ValidationException;
import java.net.URI;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class LocaleUtilsTest {

  private static final URI BASE_URI = URI.create("https://idp.example.com");

  @Test
  void test_parseAcceptLanguageHeader_multipleValidCountryRegion() {
    // when
    var locales = LocaleUtils.negotiatePreferredLocales("de-DE,en-US;q=0.5");

    // then
    assertThat(locales.get(0).getLanguage(), is("de"));

    assertThat(locales.get(1).getLanguage(), is("en"));

    assertThat(locales.size(), is(2));
  }

  @Test
  void test_parseAcceptLanguageHeader_multipleValid_addsRegionToValidRegion() {

    var locales = LocaleUtils.negotiatePreferredLocales("de-DE,de,en,it;q=0.5");

    assertThat(locales.get(0).getLanguage(), is("de"));

    assertThat(locales.get(1).getLanguage(), is("en"));

    assertThat(locales.size(), is(2));
  }

  @Test
  void test_parseAcceptLanguageHeader_validAndInvalid_filtered() {

    var locales = LocaleUtils.negotiatePreferredLocales("el-GR,de-DE");

    // then
    assertThat(locales.get(0).getLanguage(), is("de"));

    assertThat(locales.size(), is(1));
  }

  @Test
  void test_parseAcceptLanguageHeader_validWithQualityOutOfOrder() {

    var locales = LocaleUtils.negotiatePreferredLocales("en-US;q=0.5,de-DE;q=0.8");

    // then
    assertThat(locales.get(0).getLanguage(), is("de"));
    assertThat(locales.get(1).getLanguage(), is("en"));

    // then
    assertThat(locales.size(), is(2));
  }

  @Test
  void test_parseAcceptLanguageHeader_noValidSizeZero() {

    var locales = LocaleUtils.negotiatePreferredLocales("el-GR;q=0.5,ja-JP;q=0.8");

    assertThat(locales.size(), is(0));
  }

  @Test
  void test_parseAcceptLanguageHeader_nullThrowsValidationException() {

    assertThrows(ValidationException.class, () -> LocaleUtils.negotiatePreferredLocales(null));
  }

  @Test
  void test_parseAcceptLanguageHeader_emptyThrowsValidationException() {

    assertThrows(ValidationException.class, () -> LocaleUtils.negotiatePreferredLocales(""));
  }

  @Test
  void test_parseAcceptLanguageHeader_brokenThrowsValidationException() {

    assertThrows(ValidationException.class, () -> LocaleUtils.negotiatePreferredLocales("x21;"));
  }

  @Test
  void tes_tGetLocalizedErrorMessage_simpleError() {

    var errorMessage =
        new ValidationException.LocalizedErrorMessage("error.unparsableHeader", null);
    var locale = Locale.GERMANY;

    var result = LocaleUtils.getLocalizedErrorMessage(errorMessage, locale);

    assertEquals("Fehlgeformter Accept-Language-Header-Wert kann nicht analysiert werden", result);
  }

  @Test
  void test_GetLocalizedErrorMessage_errorWithContent() {

    var errorMessage =
        new ValidationException.LocalizedErrorMessage(
            "error.badRedirect", String.valueOf(BASE_URI));
    var locale = Locale.GERMANY;

    var result = LocaleUtils.getLocalizedErrorMessage(errorMessage, locale);

    var expected =
        "Ungültige redirect_uri='%s'. Übergebener Link ist nicht gültig.".formatted(BASE_URI);
    assertEquals(expected, result);
  }
}
