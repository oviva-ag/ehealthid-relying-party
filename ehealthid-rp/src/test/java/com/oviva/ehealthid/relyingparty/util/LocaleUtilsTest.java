package com.oviva.ehealthid.relyingparty.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LocaleUtilsTest {

  private static final Set<Locale> SUPPORTED_LOCALES =
      Set.of(Locale.forLanguageTag("en-US"), Locale.forLanguageTag("de-DE"));

  @Test
  void test_parseAcceptLanguageHeader_multipleValidCountryRegion() {
    // when
    var locales = LocaleUtils.parseAcceptLanguageHeader("de-DE,en-US;q=0.5", SUPPORTED_LOCALES);

    // then
    assertThat(locales.get(0).getLanguage(), is("de"));

    assertThat(locales.get(1).getLanguage(), is("en"));

    assertThat(locales.size(), is(2));
  }

  @Test
  void test_parseAcceptLanguageHeader_multipleValid_addsRegionToValidRegion() {

    var locales = LocaleUtils.parseAcceptLanguageHeader("de-CH,de,en,it;q=0.5", SUPPORTED_LOCALES);

    assertThat(locales.get(0).getLanguage(), is("de"));

    assertThat(locales.get(1).getLanguage(), is("en"));

    assertThat(locales.size(), is(2));
  }

  @Test
  void test_parseAcceptLanguageHeader_validAndInvalid_filtered() {

    var locales = LocaleUtils.parseAcceptLanguageHeader("el-GR,de-DE", SUPPORTED_LOCALES);

    // then
    assertThat(locales.get(0).getLanguage(), is("de"));

    assertThat(locales.size(), is(1));
  }

  @Test
  void test_parseAcceptLanguageHeader_validWithQualityOutOfOrder() {

    var locales =
        LocaleUtils.parseAcceptLanguageHeader("en-US;q=0.5,de-DE;q=0.8", SUPPORTED_LOCALES);

    // then
    assertThat(locales.get(0).getLanguage(), is("de"));
    assertThat(locales.get(1).getLanguage(), is("en"));

    // then
    assertThat(locales.size(), is(2));
  }

  @Test
  void test_parseAcceptLanguageHeader_noValidSizeZero() {

    var locales =
        LocaleUtils.parseAcceptLanguageHeader("el-GR;q=0.5,ja-JP;q=0.8", SUPPORTED_LOCALES);

    assertThat(locales.size(), is(0));
  }

  @Test
  void test_parseAcceptLanguageHeader_nullThrowsValidationException() {

    assertThrows(
        IllegalArgumentException.class,
        () -> LocaleUtils.parseAcceptLanguageHeader(null, SUPPORTED_LOCALES));
  }

  @Test
  void test_parseAcceptLanguageHeader_emptyThrowsValidationException() {

    assertThrows(
        IllegalArgumentException.class,
        () -> LocaleUtils.parseAcceptLanguageHeader("", SUPPORTED_LOCALES));
  }

  @Test
  void test_parseAcceptLanguageHeader_brokenThrowsValidationException() {

    assertThrows(
        IllegalArgumentException.class,
        () -> LocaleUtils.parseAcceptLanguageHeader("x21;", SUPPORTED_LOCALES));
  }
}
