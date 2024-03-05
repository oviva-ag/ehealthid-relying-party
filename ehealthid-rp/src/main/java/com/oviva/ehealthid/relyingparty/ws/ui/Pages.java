package com.oviva.ehealthid.relyingparty.ws.ui;

import static com.oviva.ehealthid.relyingparty.util.LocaleUtils.*;

import com.oviva.ehealthid.fedclient.IdpEntry;
import com.oviva.ehealthid.relyingparty.svc.LocalizedException.Message;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Pages {

  private final TemplateRenderer renderer;

  public Pages(TemplateRenderer renderer) {
    this.renderer = renderer;
  }

  public String selectIdpForm(List<IdpEntry> identityProviders, String acceptLanguageHeaderValue) {
    identityProviders =
        identityProviders.stream().sorted(Comparator.comparing(IdpEntry::name)).toList();

    return renderer.render(
        "select-idp.html.mustache",
        Map.of("identityProviders", identityProviders),
        getNegotiatedLocale(acceptLanguageHeaderValue));
  }

  public String error(Message errorMessage, String acceptLanguageHeaderValue) {
    var localizedErrorMessage =
        formatLocalizedErrorMessage(errorMessage, getNegotiatedLocale(acceptLanguageHeaderValue));

    return renderer.render(
        "error.html.mustache",
        Map.of("errorMessage", localizedErrorMessage),
        getNegotiatedLocale(acceptLanguageHeaderValue));
  }

  private Locale getNegotiatedLocale(String acceptLanguageHeaderValue) {

    var acceptableLanguages = negotiatePreferredLocales(acceptLanguageHeaderValue);

    return acceptableLanguages.stream().findFirst().orElse(DEFAULT_LOCALE);
  }
}
