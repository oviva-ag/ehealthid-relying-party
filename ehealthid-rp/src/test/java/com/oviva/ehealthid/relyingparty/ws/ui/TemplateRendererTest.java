package com.oviva.ehealthid.relyingparty.ws.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateRendererTest {

  @Test
  void render() {

    var mf = mock(MustacheFactory.class);
    var template = mock(Mustache.class);

    var sut = new TemplateRenderer(mf);

    var name = "mytemplate.mustache";
    var scope = Map.of("message", "Hello World!");

    when(mf.compile(name)).thenReturn(template);

    // when
    var got = sut.render(name, scope);

    // then
    verify(mf).compile(name);
    verify(template).execute(any(), eq(scope));
  }
}
