package com.oviva.ehealthid.relyingparty.ws.ui;

import com.oviva.ehealthid.fedclient.IdpEntry;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class Pages {

  private final TemplateRenderer renderer;

  public Pages(TemplateRenderer renderer) {
    this.renderer = renderer;
  }

  public String selectIdpForm(List<IdpEntry> identityProviders) {
    identityProviders =
        identityProviders.stream().sorted(Comparator.comparing(IdpEntry::name)).toList();
    return renderer.render(
        "select-idp.html.mustache", Map.of("identityProviders", identityProviders));
  }

  public String error(String message) {
    return renderer.render("error.html.mustache", Map.of("message", message));
  }
}
