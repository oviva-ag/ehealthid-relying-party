package com.oviva.ehealthid.cli.forms;

import com.github.mustachejava.DefaultMustacheFactory;
import java.io.StringReader;
import java.io.StringWriter;

public class MustacheRenderer {

  public static String render(String template, Object model) {

    var mf = new DefaultMustacheFactory();
    var compiledTemplate = mf.compile(new StringReader(template), "-");

    var w = new StringWriter();

    compiledTemplate.execute(w, model);
    return w.toString();
  }
}
