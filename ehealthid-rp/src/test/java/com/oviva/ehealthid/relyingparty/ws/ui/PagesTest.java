package com.oviva.ehealthid.relyingparty.ws.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.oviva.ehealthid.fedclient.IdpEntry;
import com.oviva.ehealthid.relyingparty.svc.LocalizedException.Message;
import com.oviva.ehealthid.relyingparty.test.Fixtures;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.apache.commons.text.StringEscapeUtils;
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
            null,
            Locale.US);

    assertEquals(Fixtures.getUtf8String("pages_golden_idp-select-form.bin"), rendered);
  }

  @Test
  void selectIdpForm_withAppUri() {
    var sut = new Pages(renderer);

    var idps = List.of(new IdpEntry("https://a.example.com", "AoK Tesfalen", null));
    var appUri = URI.create("https://app.example.com");
    var locale = Locale.US;

    var rendered = sut.selectIdpForm(idps, appUri, locale);

    assertTrue(rendered.contains(appUri.toString()));
    assertTrue(rendered.contains("Back to app"));
  }

  @ParameterizedTest
  @MethodSource("provideLanguageAndMessageForSelectIdp")
  void selectIdpForm_withInternationalisation(Locale locale, String message) {
    var sut = new Pages(renderer);

    var rendered =
        sut.selectIdpForm(
            List.of(
                new IdpEntry("https://a.example.com", "AoK Tesfalen", null),
                new IdpEntry("https://b.example.com", "Siemens", null),
                new IdpEntry("https://c.example.com", "Zuse", null),
                new IdpEntry("https://d.example.com", "Barmer", null)),
            URI.create("https://app.example.com"),
            locale);

    assertTrue(rendered.contains(message));
  }

  @Test
  void jumpToApp_withFixture() {
    var sut = new Pages(renderer);

    var rendered = sut.jumpToApp(URI.create("https://idp.example.com"), Locale.GERMANY);

    assertEquals(Fixtures.getUtf8String("pages_golden_jump-to-app.bin"), rendered);
  }

  @Test
  void error_withFixture() {
    var sut = new Pages(renderer);

    var rendered = sut.error(new Message("error.serverError", ""), null, Locale.US);

    assertEquals(Fixtures.getUtf8String("pages_golden_error.bin"), rendered);
  }

  @Test
  void error_withAppUri() {
    var sut = new Pages(renderer);
    var message = new Message("error.serverError", "");
    var appUri = URI.create("https://app.example.com");
    var locale = Locale.US;

    var rendered = sut.error(message, appUri, locale);

    assertTrue(rendered.contains(appUri.toString()));
    assertTrue(rendered.contains("Back to app"));
  }

  @ParameterizedTest
  @MethodSource("provideKeyAndMessageError")
  void error_simpleError(Locale locale, String messageKey, String message) {
    var sut = new Pages(renderer);

    var rendered = sut.error(new Message(messageKey, ""), null, locale);

    assertTrue(rendered.contains(message));
  }

  @ParameterizedTest
  @MethodSource("provideKeyWithDynamicContentMessageError")
  void error_errorWithDynamicContent(Locale locale, String messageKey, String message) {
    var sut = new Pages(renderer);
    var uri = URI.create("https://idp.example.com");

    var rendered = sut.error(new Message(messageKey, String.valueOf(uri)), null, locale);
    assertTrue(StringEscapeUtils.unescapeHtml4(rendered).contains(message));
  }

  @Test
  void success_withFixture() {
    var sut = new Pages(renderer);
    var redirectUri = URI.create("https://app.example.com/callback");
    var locale = Locale.US;

    var rendered = sut.success(redirectUri, locale);

    assertEquals(Fixtures.getUtf8String("pages_golden_success.bin"), rendered);
  }

  @Test
  void success_withRedirectUri() {
    var sut = new Pages(renderer);
    var redirectUri = URI.create("https://app.example.com/callback");
    var locale = Locale.US;

    var rendered = sut.success(redirectUri, locale);

    assertTrue(rendered.contains(redirectUri.toString()));
    assertTrue(rendered.contains("Continue"));
  }

  @ParameterizedTest
  @MethodSource("provideLanguageAndMessageForSuccess")
  void success_withInternationalisation(Locale locale, String message) {
    var sut = new Pages(renderer);
    var redirectUri = URI.create("https://app.example.com/callback");

    var rendered = sut.success(redirectUri, locale);

    assertTrue(rendered.contains(message));
  }

  private static Stream<Arguments> provideLanguageAndMessageForSelectIdp() {
    return Stream.of(
        Arguments.of(Locale.US, "en-US"),
        Arguments.of(Locale.GERMANY, "de-DE"),
        Arguments.of(Locale.US, "Login with GesundheitsID"),
        Arguments.of(Locale.GERMANY, "Anmeldung mit GesundheitsID"),
        Arguments.of(Locale.US, "Select your GesundheitsID provider."),
        Arguments.of(Locale.GERMANY, "Wähle deinen GesundheitsID-Anbieter aus."),
        Arguments.of(Locale.US, "Login"),
        Arguments.of(Locale.GERMANY, "Einloggen"),
        Arguments.of(Locale.US, "Back to app"),
        Arguments.of(Locale.GERMANY, "Zurück zur App"));
  }

  private static Stream<Arguments> provideKeyAndMessageError() {
    return Stream.of(
        Arguments.of(Locale.US, "error.login", "Login with GesundheitsID"),
        Arguments.of(Locale.GERMANY, "error.login", "Anmeldung mit GesundheitsID"),
        Arguments.of(
            Locale.US, "error.serverError", "Ohh no! Unexpected server error. Please try again."),
        Arguments.of(
            Locale.GERMANY,
            "error.serverError",
            "Ohh nein! Unerwarteter Serverfehler. Bitte versuchen Sie es erneut."),
        Arguments.of(
            Locale.US, "error.noProvider", "No identity provider selected. Please go back"),
        Arguments.of(
            Locale.GERMANY,
            "error.noProvider",
            "Kein Identitätsanbieter ausgewählt. Bitte zurückgehen."),
        Arguments.of(
            Locale.US,
            "error.invalidSession",
            "Oops! The session is unknown or has expired. Please try again."),
        Arguments.of(
            Locale.GERMANY,
            "error.invalidSession",
            "Ups! Die Sitzung ist unbekannt oder abgelaufen. Bitte versuche es erneut."),
        Arguments.of(Locale.US, "backToApp", "Back to app"),
        Arguments.of(Locale.GERMANY, "backToApp", "Zurück zur App"));
  }

  private static Stream<Arguments> provideKeyWithDynamicContentMessageError() {
    return Stream.of(
        Arguments.of(
            Locale.US,
            "error.insecureRedirect",
            "Insecure redirect_uri='https://idp.example.com'. Misconfigured server, please use 'https'."),
        Arguments.of(
            Locale.GERMANY,
            "error.insecureRedirect",
            "Unsicherer redirect_uri='https://idp.example.com'. Falsch konfigurierter Server, bitte verwenden Sie 'https'."),
        Arguments.of(
            Locale.US,
            "error.badRedirect",
            "Bad redirect_uri='https://idp.example.com'. Passed link is not valid."),
        Arguments.of(
            Locale.GERMANY,
            "error.badRedirect",
            "Ungültige redirect_uri='https://idp.example.com'. Übergebener Link ist nicht gültig."),
        Arguments.of(
            Locale.US,
            "error.untrustedRedirect",
            "Untrusted redirect_uri=https://idp.example.com. Misconfigured server."),
        Arguments.of(
            Locale.GERMANY,
            "error.untrustedRedirect",
            "Nicht vertrauenswürdiger redirect_uri=https://idp.example.com. Falsch konfigurierter Server."));
  }

  private static Stream<Arguments> provideLanguageAndMessageForSuccess() {
    return Stream.of(
        Arguments.of(Locale.US, "GesundheitsID successfully linked!"),
        Arguments.of(Locale.GERMANY, "GesundheitsID erfolgreich verknüpft!"),
        Arguments.of(
            Locale.US, "Your GesundheitsID is now linked. Simply log in with it in the future."),
        Arguments.of(
            Locale.GERMANY,
            "Deine GesundheitsID ist jetzt verknüpft. Melde dich künftig einfach damit an."),
        Arguments.of(Locale.US, "Continue"),
        Arguments.of(Locale.GERMANY, "Weiter"));
  }
}
