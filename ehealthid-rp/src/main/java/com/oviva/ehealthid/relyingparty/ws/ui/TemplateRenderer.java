package com.oviva.ehealthid.relyingparty.ws.ui;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.MustacheFactory;
import com.github.mustachejava.functions.TranslateBundleFunction;
import java.io.StringWriter;
import java.util.Locale;
import java.util.Map;

public class TemplateRenderer {

  private MustacheFactory mf = new DefaultMustacheFactory("www");

  public TemplateRenderer() {}

  public TemplateRenderer(MustacheFactory mf) {
    this.mf = mf;
  }

  public String render(String name, Map<String, Object> scope, Locale locale) {
    var template = mf.compile(name);
    var w = new StringWriter();
    scope.put("trans", new TranslateBundleFunction("i18n", locale));
    template.execute(w, scope);
    return w.toString();
  }
}
