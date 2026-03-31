package com.oviva.ehealthid.cli;

import static com.oviva.ehealthid.cli.forms.RegistratonFormRenderer.Model.Environment.PU;
import static com.oviva.ehealthid.cli.forms.RegistratonFormRenderer.Model.Environment.TU;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.oviva.ehealthid.cli.forms.RegistratonFormRenderer;
import com.oviva.ehealthid.cli.forms.RegistratonFormRenderer.Model;
import java.net.URI;
import java.security.SecureRandom;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RegistratonFormRendererTest {

  @Test
  void render_PU() throws JOSEException {
    var key = getKey();
    var xml =
        RegistratonFormRenderer.render(
            new Model(
                "VFS_DiGA_Test",
                "FDmyDiGAMemb",
                "My DiGA Org",
                "My DiGA",
                "bobby.tables@example.com",
                URI.create("https://mydiga.example.com"),
                PU,
                List.of("openid", "urn:telematik:versicherter", "urn:telematik:email"),
                List.of("https://mydiga.example.com/auth/callback"),
                new JWKSet(key)));

    assertEquals(
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <registrierungtifoederation>
          <teilnehmertyp>Fachdienst</teilnehmertyp>
          <betriebsumgebung>PU</betriebsumgebung>
          <kontaktemail>bobby.tables@example.com</kontaktemail>
          <vfsbestaetigung>VFS_DiGA_Test</vfsbestaetigung>
          <zuweisungsgruppe>ORG-0229:BT-0170</zuweisungsgruppe>
          <memberid>FDmyDiGAMemb</memberid>
          <organisationsname>My DiGA Org</organisationsname>
          <fachdienstname>My DiGA</fachdienstname>
          <fachdiensturi>https://mydiga.example.com</fachdiensturi>
          <scopes>
            <scope>openid</scope>
            <scope>urn:telematik:versicherter</scope>
            <scope>urn:telematik:email</scope>
          </scopes>
          <claims/>
          <redirect_uris>
            <redirect_uri>https://mydiga.example.com/auth/callback</redirect_uri>
          </redirect_uris>
          <publickeysjwt>
            <publickey>
              <kid>wXOS7cMWjpUGqySA-BwnbmiQSaaWBpEmy4xf08CHQXQ</kid>
              <key>-----BEGIN PUBLIC KEY-----&#10;MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE2lFRaRRzJx0Tspr8nT526HTY0LCQ&#10;g8WJhXI+LNXvmjbYokHMfZzM9xerR/u+Y/q1VK9NSH2cbXfGAmT24gC4DQ&#61;&#61;&#10;-----END PUBLIC KEY-----&#10;</key>
            </publickey>
          </publickeysjwt>
        </registrierungtifoederation>
        """,
        xml);
  }

  @Test
  void render_TU() throws JOSEException {
    var key = getKey();
    var xml =
        RegistratonFormRenderer.render(
            new Model(
                null,
                "FDmyDiGAMemb",
                "My DiGA Org",
                "My DiGA",
                "bobby.tables@example.com",
                URI.create("https://mydiga.example.com"),
                TU,
                List.of("openid", "urn:telematik:versicherter", "urn:telematik:email"),
                List.of("https://mydiga.example.com/auth/callback"),
                new JWKSet(key)));

    assertEquals(
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <registrierungtifoederation>
          <teilnehmertyp>Fachdienst</teilnehmertyp>
          <betriebsumgebung>TU</betriebsumgebung>
          <kontaktemail>bobby.tables@example.com</kontaktemail>
          <vfsbestaetigung></vfsbestaetigung>
          <zuweisungsgruppe>ORG-0229:BT-0170</zuweisungsgruppe>
          <memberid>FDmyDiGAMemb</memberid>
          <organisationsname>My DiGA Org</organisationsname>
          <fachdienstname>My DiGA</fachdienstname>
          <fachdiensturi>https://mydiga.example.com</fachdiensturi>
          <scopes>
            <scope>openid</scope>
            <scope>urn:telematik:versicherter</scope>
            <scope>urn:telematik:email</scope>
          </scopes>
          <claims/>
          <redirect_uris>
            <redirect_uri>https://mydiga.example.com/auth/callback</redirect_uri>
          </redirect_uris>
          <publickeysjwt>
            <publickey>
              <kid>wXOS7cMWjpUGqySA-BwnbmiQSaaWBpEmy4xf08CHQXQ</kid>
              <key>-----BEGIN PUBLIC KEY-----&#10;MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE2lFRaRRzJx0Tspr8nT526HTY0LCQ&#10;g8WJhXI+LNXvmjbYokHMfZzM9xerR/u+Y/q1VK9NSH2cbXfGAmT24gC4DQ&#61;&#61;&#10;-----END PUBLIC KEY-----&#10;</key>
            </publickey>
          </publickeysjwt>
        </registrierungtifoederation>
        """,
        xml);
  }

  private ECKey getKey() throws JOSEException {
    return new ECKeyGenerator(Curve.P_256)
        .keyIDFromThumbprint(true)
        .keyUse(KeyUse.SIGNATURE)
        .secureRandom(
            new SecureRandom() {
              private final Random badRandom = new Random(0xBAD);

              @Override
              public void nextBytes(byte[] bytes) {
                badRandom.nextBytes(bytes);
              }
            })
        .generate();
  }
}
