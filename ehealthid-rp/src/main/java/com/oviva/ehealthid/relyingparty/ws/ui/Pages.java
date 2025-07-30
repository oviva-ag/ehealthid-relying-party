package com.oviva.ehealthid.relyingparty.ws.ui;

import static com.oviva.ehealthid.relyingparty.util.LocaleUtils.*;

import com.oviva.ehealthid.fedclient.IdpEntry;
import com.oviva.ehealthid.relyingparty.svc.LocalizedException.Message;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Pages {

  private final TemplateRenderer renderer;

  public Pages(TemplateRenderer renderer) {
    this.renderer = renderer;
  }

  public String selectIdpForm(List<IdpEntry> identityProviders, Locale locale) {
    identityProviders =
        identityProviders.stream().sorted(Comparator.comparing(IdpEntry::name)).toList();

    return renderer.render(
        "select-idp.html.mustache", Map.of("identityProviders", identityProviders), locale);
  }

  public String jumpToApp(URI destination, Locale locale) {

    return renderer.render(
        "jump-to-app.html.mustache", Map.of("destination", destination.toString()), locale);
  }

  public String error(Message errorMessage, Locale locale) {
    var localizedErrorMessage = formatLocalizedErrorMessage(errorMessage, locale);

    return renderer.render(
        "error.html.mustache", Map.of("errorMessage", localizedErrorMessage), locale);
  }
}
