package com.oviva.ehealthid.cli.forms;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import java.net.URI;
import java.security.Key;
import java.util.Base64;
import java.util.List;

public class RegistratonFormRenderer {

  private static final String XML_TEMPLATE =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <registrierungtifoederation>
        <teilnehmertyp>Fachdienst</teilnehmertyp>
        <betriebsumgebung>{{environment}}</betriebsumgebung>
        <kontaktemail>{{contactEmail}}</kontaktemail>
        <vfsbestaetigung>{{vfsConfirmation}}</vfsbestaetigung>
        <zuweisungsgruppe>ORG-0229:BT-0170</zuweisungsgruppe>
        <memberid>{{memberId}}</memberid>
        <organisationsname>{{organisationName}}</organisationsname>
        <fachdienstname>{{fachdienstName}}</fachdienstname>
        <fachdiensturi>{{fachdienstUri}}</fachdiensturi>
        <scopes>
      {{#scopes}}
          <scope>{{value}}</scope>
      {{/scopes}}
        </scopes>
        <claims/>
        <redirect_uris>
      {{#redirectUris}}
          <redirect_uri>{{value}}</redirect_uri>
      {{/redirectUris}}
        </redirect_uris>
        <publickeysjwt>
      {{#publicKeys}}
          <publickey>
            <kid>{{kid}}</kid>
            <key>{{pem}}</key>
          </publickey>
      {{/publicKeys}}
        </publickeysjwt>
      </registrierungtifoederation>
      """;

  public static String render(Model m) {
    var rm = RenderModel.fromModel(m);
    return MustacheRenderer.render(XML_TEMPLATE, rm);
  }

  public record Model(
      String vfsConfirmation,
      String memberId,
      String organisationName,
      String fachdienstName,
      String contactEmail,
      URI issuerUri,
      Environment environment,
      List<String> scopes,
      List<String> redirectUris,
      JWKSet jwks) {

    public enum Environment {
      RU,
      TU,
      PU
    }
  }

  record RenderModel(
      String vfsConfirmation,
      String memberId,
      String organisationName,
      String fachdienstName,
      String fachdienstUri,
      String environment,
      String contactEmail,
      List<StringValue> scopes,
      List<StringValue> redirectUris,
      List<PublicKey> publicKeys) {

    static RenderModel fromModel(Model m) {
      return new RenderModel(
          emptyIfNull(m.vfsConfirmation()),
          m.memberId(),
          emptyIfNull(m.organisationName()),
          emptyIfNull(m.fachdienstName()),
          m.issuerUri().toString(),
          m.environment().name(),
          emptyIfNull(m.contactEmail()),
          m.scopes().stream().map(StringValue::new).toList(),
          m.redirectUris().stream().map(StringValue::new).toList(),
          m.jwks().getKeys().stream().map(RenderModel::toPublicKey).toList());
    }

    private static String emptyIfNull(String s) {
      return s == null ? "" : s;
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

    record StringValue(String value) {}

    record PublicKey(String kid, String pem) {}
  }
}
