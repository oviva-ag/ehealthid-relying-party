package com.oviva.ehealthid.cli.forms;

import java.net.URI;

public class DeregistrationFormRenderer {

  private static final String TEMPLATE =
      """
<?xml version="1.0" encoding="UTF-8"?>
<deregistrierungtifoederation>
  <betriebsumgebung>{{environment}}</betriebsumgebung>
  <memberid>{{memberId}}</memberid>
  <fachdiensturi>{{issuerUri}}</fachdiensturi>
  <grund>{{reason}}</grund>
</deregistrierungtifoederation>
""";

  record RenderModel(String memberId, String issuerUri, String environment, String reason) {

    static RenderModel fromModel(Model m) {
      return new RenderModel(
          m.memberId(), m.issuerUri().toString(), m.environment().name(), "Ungenutzt");
    }
  }

  public record Model(String memberId, URI issuerUri, Environment environment) {

    public enum Environment {
      RU,
      TU,
      PU
    }
  }

  public static String render(Model m) {
    return MustacheRenderer.render(TEMPLATE, RenderModel.fromModel(m));
  }
}
