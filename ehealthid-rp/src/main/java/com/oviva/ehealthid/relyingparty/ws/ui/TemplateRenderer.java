package com.oviva.ehealthid.relyingparty.ws.ui;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.MustacheFactory;
import java.io.StringWriter;

public class TemplateRenderer {

  private MustacheFactory mf = new DefaultMustacheFactory("www");

  public TemplateRenderer() {}

  public TemplateRenderer(MustacheFactory mf) {
    this.mf = mf;
  }

  public String render(String name, Object scope) {

    var template = mf.compile(name);
    var w = new StringWriter();
    template.execute(w, scope);
    return w.toString();
  }
}
