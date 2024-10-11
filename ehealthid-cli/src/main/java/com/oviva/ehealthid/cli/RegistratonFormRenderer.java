package com.oviva.ehealthid.cli;

import com.github.mustachejava.DefaultMustacheFactory;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.oviva.ehealthid.cli.RegistratonFormRenderer.Model.Scope;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.security.Key;
import java.util.Base64;
import java.util.List;

public class RegistratonFormRenderer {

  // the PU registration XML differs from others
  private static final String XML_TEMPLATE_PROD =
      """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <registrierungtifoederation>
                      <datendesantragstellers>
                      <vfsbestaetigung>{{vfsConfirmation}}</vfsbestaetigung>
                      <teilnehmertyp>Fachdienst</teilnehmertyp>
                      <organisationsname>{{organisationName}}</organisationsname>
                      <memberid>{{memberId}}</memberid>
                      <zwg>ORG-0229:BT-0170</zwg>
                      <issueruri>{{issuerUri}}</issueruri>
                      <scopes>
                        <scopealter>{{scopeAge}}</scopealter>
                        <scopeanzeigename>{{scopeDisplayName}}</scopeanzeigename>
                        <scopeemail>{{scopeEmail}}</scopeemail>
                        <scopegeschlecht>{{scopeGender}}</scopegeschlecht>
                        <scopegeburtsdatum>{{scopeDateOfBirth}}</scopegeburtsdatum>
                        <scopevorname>{{scopeFirstName}}</scopevorname>
                        <scopenachname>{{scopeLastName}}</scopenachname>
                        <scopeversicherter>{{scopeInsuredPerson}}</scopeversicherter>
                      </scopes>
                      {{#publicKeys}}
                      <publickeys>
                        <kidjwt>{{kid}}</kidjwt>
                        <pubkeyjwt>{{pem}}</pubkeyjwt>
                      </publickeys>
                      {{/publicKeys}}
                      </datendesantragstellers>
                    </registrierungtifoederation>
                    """;

  private static final String XML_TEMPLATE_TEST =
      """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <registrierungtifoederation>
                       <datendesantragstellers>
                         <kontaktemail>{{contactEmail}}</kontaktemail>
                         <teilnehmertyp>Fachdienst</teilnehmertyp>
                         <betriebsumgebung>{{environment}}</betriebsumgebung>
                         <organisationsname>{{organisationName}}</organisationsname>
                         <memberid>{{memberId}}</memberid>
                         <zwg>ORG-0001:BT-0144</zwg>
                         <issueruri>{{issuerUri}}</issueruri>
                         <scopes>
                           <scopealter>{{scopeAge}}</scopealter>
                           <scopeanzeigename>{{scopeDisplayName}}</scopeanzeigename>
                           <scopeemail>{{scopeEmail}}</scopeemail>
                           <scopegeschlecht>{{scopeGender}}</scopegeschlecht>
                           <scopegeburtsdatum>{{scopeDateOfBirth}}</scopegeburtsdatum>
                           <scopevorname>{{scopeFirstName}}</scopevorname>
                           <scopenachname>{{scopeLastName}}</scopenachname>
                           <scopeversicherter>{{scopeInsuredPerson}}</scopeversicherter>
                         </scopes>
                         {{#publicKeys}}
                         <publickeys>
                           <kidjwt>{{kid}}</kidjwt>
                           <pubkeyjwt>{{pem}}</pubkeyjwt>
                         </publickeys>
                         {{/publicKeys}}
                       </datendesantragstellers>
                    </registrierungtifoederation>
                    """;

  public static String render(Model m) {

    return switch (m.environment()) {
      case PU -> renderProductiveEnvironment(m);
      default -> renderTestEnvironment(m);
    };
  }

  private static String renderProductiveEnvironment(Model m) {
    return renderTemplate(XML_TEMPLATE_PROD, m);
  }

  private static String renderTestEnvironment(Model m) {
    return renderTemplate(XML_TEMPLATE_TEST, m);
  }

  private static String renderTemplate(String template, Model m) {

    var mf = new DefaultMustacheFactory();
    var compiledTemplate = mf.compile(new StringReader(template), "entity-statement-registration");

    var w = new StringWriter();

    var rm = RenderModel.fromModel(m);
    compiledTemplate.execute(w, rm);
    return w.toString();
  }

  public record Model(
      String vfsConfirmation,
      String memberId,
      String organisationName,
      String contactEmail,
      URI issuerUri,
      Environment environment,
      List<Scope> scopes,
      JWKSet jwks) {

    enum Environment {
      RU,
      TU,
      PU
    }

    // https://gemspec.gematik.de/docs/gemSpec/gemSpec_IDP_Sek/gemSpec_IDP_Sek_V2.3.0/index.html#A_22989-01
    enum Scope {
      AGE,
      DISPLAY_NAME,
      EMAIL,
      GENDER,
      DATE_OF_BIRTH,
      FIRST_NAME,
      LAST_NAME,
      INSURED_PERSON
    }
  }

  record RenderModel(
      String vfsConfirmation,
      String memberId,
      String organisationName,
      String issuerUri,
      String environment,
      String contactEmail,
      int scopeAge,
      int scopeDisplayName,
      int scopeEmail,
      int scopeGender,
      int scopeDateOfBirth,
      int scopeFirstName,
      int scopeLastName,
      int scopeInsuredPerson,
      List<PublicKey> publicKeys) {

    public static RenderModel fromModel(Model m) {

      return new RenderModel(
          m.vfsConfirmation(),
          m.memberId(),
          m.organisationName(),
          m.issuerUri().toString(),
          m.environment().name(),
          m.contactEmail(),
          m.scopes().contains(Scope.AGE) ? 1 : 0,
          m.scopes().contains(Scope.DISPLAY_NAME) ? 1 : 0,
          m.scopes().contains(Scope.EMAIL) ? 1 : 0,
          m.scopes().contains(Scope.GENDER) ? 1 : 0,
          m.scopes().contains(Scope.DATE_OF_BIRTH) ? 1 : 0,
          m.scopes().contains(Scope.FIRST_NAME) ? 1 : 0,
          m.scopes().contains(Scope.LAST_NAME) ? 1 : 0,
          m.scopes().contains(Scope.INSURED_PERSON) ? 1 : 0,
          m.jwks().getKeys().stream().map(RenderModel::toPublicKey).toList());
    }

    private static PublicKey toPublicKey(JWK key) {
      try {
        var ecKey = key.toECKey();
        var pem = encodeAsPem(ecKey.toPublicKey(), "PUBLIC KEY");
        return new PublicKey(ecKey.getKeyID(), pem);
      } catch (JOSEException e) {
        throw new IllegalArgumentException("bad key: '%s'".formatted(key), e);
      }
    }

    private static String encodeAsPem(Key k, String type) {

      var encoded = Base64.getEncoder().encodeToString(k.getEncoded());

      var sb = new StringBuilder();
      sb.append("-----BEGIN %s-----%n".formatted(type));

      while (!encoded.isEmpty()) {
        var end = Math.min(64, encoded.length());
        var line = encoded.substring(0, end);
        sb.append(line).append('\n');
        encoded = encoded.substring(end);
      }

      sb.append("-----END %s-----%n".formatted(type));
      return sb.toString();
    }

    record PublicKey(String kid, String pem) {}
  }
}
