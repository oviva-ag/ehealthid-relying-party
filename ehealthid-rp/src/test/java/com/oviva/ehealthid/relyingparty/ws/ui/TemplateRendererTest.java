package com.oviva.ehealthid.relyingparty.ws.ui;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.github.mustachejava.functions.TranslateBundleFunction;
import java.util.HashMap;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class TemplateRendererTest {

  @Test
  void render() {

    var mf = mock(MustacheFactory.class);
    var template = mock(Mustache.class);

    var sut = new TemplateRenderer(mf);

    var name = "mytemplate.mustache";
    var translateBundleFunction = new TranslateBundleFunction("i18n", Locale.US);

    var scope = new HashMap<String, Object>();
    scope.put("trans", translateBundleFunction);

    when(mf.compile(name)).thenReturn(template);

    // when
    var got = sut.render(name, scope, Locale.US);

    // then
    verify(mf).compile(name);
    verify(template).execute(any(), eq(scope));
  }
}
