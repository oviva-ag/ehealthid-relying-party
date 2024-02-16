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

  private static final String XML_TEMPLATE =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <registrierungtifoederation>
         <datendesantragstellers>
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

    var mf = new DefaultMustacheFactory();
    var template = mf.compile(new StringReader(XML_TEMPLATE), "entity-statement-registration");

    var w = new StringWriter();

    var rm = RenderModel.fromModel(m);
    template.execute(w, rm);
    return w.toString();
  }

  public record Model(
      String memberId,
      String organisationName,
      URI issuerUri,
      Environment environment,
      List<Scope> scopes,
      JWKSet jwks) {

    enum Environment {
      RU,
      TU,
      PU
    }

    enum Scope {
      AGE,
      DISPLAY_NAME,
      EMAIL,
      GENDER,
      DATE_OF_BIRTH,
      FIRST_NAME,
      INSURED_PERSON
    }
  }

  record RenderModel(
      String memberId,
      String organisationName,
      String issuerUri,
      String environment,
      int scopeAge,
      int scopeDisplayName,
      int scopeEmail,
      int scopeGender,
      int scopeDateOfBirth,
      int scopeFirstName,
      int scopeInsuredPerson,
      List<PublicKey> publicKeys) {

    public static RenderModel fromModel(Model m) {

      return new RenderModel(
          m.memberId(),
          m.organisationName(),
          m.issuerUri().toString(),
          m.environment().name(),
          m.scopes().contains(Scope.AGE) ? 1 : 0,
          m.scopes().contains(Scope.DISPLAY_NAME) ? 1 : 0,
          m.scopes().contains(Scope.EMAIL) ? 1 : 0,
          m.scopes().contains(Scope.GENDER) ? 1 : 0,
          m.scopes().contains(Scope.DATE_OF_BIRTH) ? 1 : 0,
          m.scopes().contains(Scope.FIRST_NAME) ? 1 : 0,
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
