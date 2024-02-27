package com.oviva.ehealthid.relyingparty.ws.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.jknack.handlebars.internal.text.StringEscapeUtils;
import com.oviva.ehealthid.fedclient.IdpEntry;
import com.oviva.ehealthid.relyingparty.test.Fixtures;
import java.net.URI;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PagesTest {

  private final TemplateRenderer renderer = new TemplateRenderer();

  @Test
  void selectIdpForm_withFixture() {
    var sut = new Pages(renderer);

    var rendered =
        sut.selectIdpForm(
            List.of(
                new IdpEntry("https://a.example.com", "AoK Tesfalen", null),
                new IdpEntry("https://b.example.com", "Siemens", null),
                new IdpEntry("https://c.example.com", "Zuse", null),
                new IdpEntry("https://d.example.com", "Barmer", null)),
            "en-US");

    assertEquals(Fixtures.getUtf8String("pages_golden_idp-select-form.bin"), rendered);
  }

  @ParameterizedTest
  @MethodSource("provideLanguageAndMessageForSelectIdp")
  void selectIdpForm_withInternationalisation(String language, String message) {
    var sut = new Pages(renderer);

    var rendered =
        sut.selectIdpForm(
            List.of(
                new IdpEntry("https://a.example.com", "AoK Tesfalen", null),
                new IdpEntry("https://b.example.com", "Siemens", null),
                new IdpEntry("https://c.example.com", "Zuse", null),
                new IdpEntry("https://d.example.com", "Barmer", null)),
            language);

    assertTrue(rendered.contains(message));
  }

  @Test
  void error_withFixture() {
    var sut = new Pages(renderer);

    var rendered = sut.error("error.genericError", "", "en-US");

    assertEquals(Fixtures.getUtf8String("pages_golden_error.bin"), rendered);
  }

  @ParameterizedTest
  @MethodSource("provideKeyAndMessageError")
  void error_simpleError(String language, String messageKey, String message) {
    var sut = new Pages(renderer);

    var rendered = sut.error(messageKey, "", language);

    assertTrue(rendered.contains(message));
  }

  @ParameterizedTest
  @MethodSource("provideKeyWithDynamicContentMessageError")
  void error_errorWithDynamicContent(String language, String messageKey, String message) {
    var sut = new Pages(renderer);
    var uri = URI.create("https://idp.example.com");

    var rendered = sut.error(messageKey, String.valueOf(uri), language);
    assertTrue(StringEscapeUtils.unescapeHtml4(rendered).contains(message));
  }

  private static Stream<Arguments> provideLanguageAndMessageForSelectIdp() {
    return Stream.of(
        Arguments.of("en-US", "en"),
        Arguments.of("de-DE", "de"),
        Arguments.of("en-US", "Login with GesundheitsID"),
        Arguments.of("de-DE", "Anmeldung mit GesundheitsID"),
        Arguments.of("en-US", "Select your GesundheitsID Provider"),
        Arguments.of("de-DE", "Wählen Sie Ihren GesundheitsID Anbieter"),
        Arguments.of("en-US", "Login"),
        Arguments.of("de-DE", "Einloggen"));
  }

  private static Stream<Arguments> provideKeyAndMessageError() {
    return Stream.of(
        Arguments.of("en-US", "error.login", "Login with GesundheitsID"),
        Arguments.of("de-DE", "error.login", "Anmeldung mit GesundheitsID"),
        Arguments.of(
            "en-US", "error.serverError", "Ohh no! Unexpected server error. Please try again."),
        Arguments.of(
            "de-DE",
            "error.serverError",
            "Ohh nein! Unerwarteter Serverfehler. Bitte versuchen Sie es erneut."),
        Arguments.of("en-US", "error.noProvider", "No identity provider selected. Please go back"),
        Arguments.of(
            "de-DE", "error.noProvider", "Kein Identitätsanbieter ausgewählt. Bitte zurückgehen."),
        Arguments.of(
            "en-US",
            "error.invalidSession",
            "Oops, session unknown or expired. Please start again."),
        Arguments.of(
            "de-DE",
            "error.invalidSession",
            "Oops, Sitzung unbekannt oder abgelaufen. Bitte starten Sie erneut."));
  }

  private static Stream<Arguments> provideKeyWithDynamicContentMessageError() {
    return Stream.of(
        Arguments.of(
            "en-US",
            "error.insecureRedirect",
            "Insecure redirect_uri='https://idp.example.com'. Misconfigured server, please use 'https'."),
        Arguments.of(
            "de-DE",
            "error.insecureRedirect",
            "Unsicherer redirect_uri='https://idp.example.com'. Falsch konfigurierter Server, bitte verwenden Sie 'https'."),
        Arguments.of(
            "en-US",
            "error.badRedirect",
            "Bad redirect_uri='https://idp.example.com'. Passed link is not valid."),
        Arguments.of(
            "de-DE",
            "error.badRedirect",
            "Ungültige redirect_uri='https://idp.example.com'. Übergebener Link ist nicht gültig."),
        Arguments.of(
            "en-US",
            "error.untrustedRedirect",
            "Untrusted redirect_uri=https://idp.example.com. Misconfigured server."),
        Arguments.of(
            "de-DE",
            "error.untrustedRedirect",
            "Nicht vertrauenswürdiger redirect_uri=https://idp.example.com. Falsch konfigurierter Server."));
  }
}
