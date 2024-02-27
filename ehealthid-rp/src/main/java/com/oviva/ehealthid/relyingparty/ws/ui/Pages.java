package com.oviva.ehealthid.relyingparty.ws.ui;

import com.oviva.ehealthid.fedclient.IdpEntry;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

public class Pages {

  private final TemplateRenderer renderer;

  public Pages(TemplateRenderer renderer) {
    this.renderer = renderer;
  }

  public String selectIdpForm(List<IdpEntry> identityProviders, String language) {
    identityProviders =
        identityProviders.stream().sorted(Comparator.comparing(IdpEntry::name)).toList();
    var map = new HashMap<String, Object>();
    map.put("identityProviders", identityProviders);
    return renderer.render("select-idp.html.mustache", map, Locale.forLanguageTag(language));
  }

  public String error(String messageKey, String dynamicContent, String language) {
    var map = new HashMap<String, Object>();
    var localizedErrorMessage = getLocalizedErrorMessage(messageKey, dynamicContent, language);
    map.put("errorMessage", localizedErrorMessage);
    return renderer.render("error.html.mustache", map, Locale.forLanguageTag(language));
  }

  private String getLocalizedErrorMessage(
      String messageKey, String dynamicContent, String language) {
    var bundle = ResourceBundle.getBundle("i18n", Locale.forLanguageTag(language));
    var localizedMessage = bundle.getString(messageKey);

    if (dynamicContent != null && !dynamicContent.isBlank()) {
      localizedMessage = localizedMessage.formatted(dynamicContent);
    }
    return localizedMessage;
  }
}
