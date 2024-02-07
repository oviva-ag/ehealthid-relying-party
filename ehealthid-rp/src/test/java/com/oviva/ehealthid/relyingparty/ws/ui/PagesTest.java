package com.oviva.ehealthid.relyingparty.ws.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.oviva.ehealthid.fedclient.IdpEntry;
import com.oviva.ehealthid.relyingparty.test.Fixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

class PagesTest {

  private final TemplateRenderer renderer = new TemplateRenderer();

  @Test
  void selectIdpForm() {
    var sut = new Pages(renderer);

    var rendered =
        sut.selectIdpForm(
            List.of(
                new IdpEntry("https://a.example.com", "AoK Tesfalen", null),
                new IdpEntry("https://b.example.com", "Siemens", null),
                new IdpEntry("https://c.example.com", "Zuse", null),
                new IdpEntry("https://d.example.com", "Barmer", null)));

    assertEquals(Fixtures.getUtf8String("pages_golden_idp-select-form.bin"), rendered);
  }

  @Test
  void error() {
    var sut = new Pages(renderer);

    var rendered = sut.error("Ohhh noes!");

    assertEquals(Fixtures.getUtf8String("pages_golden_error.bin"), rendered);
  }
}
