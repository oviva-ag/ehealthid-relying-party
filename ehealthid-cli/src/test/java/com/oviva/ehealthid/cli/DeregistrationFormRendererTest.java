package com.oviva.ehealthid.cli;

import static org.junit.jupiter.api.Assertions.*;

import com.oviva.ehealthid.cli.forms.DeregistrationFormRenderer;
import java.net.URI;
import org.junit.jupiter.api.Test;

class DeregistrationFormRendererTest {

  @Test
  void t() {
    var ret =
        DeregistrationFormRenderer.render(
            new DeregistrationFormRenderer.Model(
                "FDOviva",
                URI.create("https://fd.example.com"),
                DeregistrationFormRenderer.Model.Environment.RU));

    assertEquals(
        """
<?xml version="1.0" encoding="UTF-8"?>
<deregistrierungtifoederation>
  <betriebsumgebung>RU</betriebsumgebung>
  <memberid>FDOviva</memberid>
  <fachdiensturi>https://fd.example.com</fachdiensturi>
  <grund>Ungenutzt</grund>
</deregistrierungtifoederation>
""",
        ret);
  }
}
