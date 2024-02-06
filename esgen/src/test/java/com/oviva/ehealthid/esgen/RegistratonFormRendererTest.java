package com.oviva.ehealthid.esgen;

import static com.oviva.ehealthid.esgen.RegistratonFormRenderer.Model.Environment.TU;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.oviva.ehealthid.esgen.RegistratonFormRenderer.Model;
import com.oviva.ehealthid.esgen.RegistratonFormRenderer.Model.Scope;
import java.net.URI;
import java.security.SecureRandom;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RegistratonFormRendererTest {

  @Test
  void render() throws JOSEException {
    var key = getKey();
    var xml =
        RegistratonFormRenderer.render(
            new Model(
                "FDmyDiGAMemb",
                "My DiGA",
                URI.create("https://mydiga.example.com"),
                TU,
                List.of(Scope.INSURED_PERSON, Scope.EMAIL, Scope.DISPLAY_NAME),
                new JWKSet(key)));

    assertEquals(
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <registrierungtifoederation>
           <datendesantragstellers>
             <teilnehmertyp>Fachdienst</teilnehmertyp>
             <betriebsumgebung>TU</betriebsumgebung>
             <organisationsname>My DiGA</organisationsname>
             <memberid>FDmyDiGAMemb</memberid>
             <zwg>ORG-0001:BT-0144</zwg>
             <issueruri>https://mydiga.example.com</issueruri>
             <scopes>
               <scopealter>0</scopealter>
               <scopeanzeigename>1</scopeanzeigename>
               <scopeemail>1</scopeemail>
               <scopegeschlecht>0</scopegeschlecht>
               <scopegeburtsdatum>0</scopegeburtsdatum>
               <scopevorname>0</scopevorname>
               <scopeversicherter>1</scopeversicherter>
             </scopes>
             <publickeys>
               <kidjwt>wXOS7cMWjpUGqySA-BwnbmiQSaaWBpEmy4xf08CHQXQ</kidjwt>
               <pubkeyjwt>-----BEGIN PUBLIC KEY-----&#10;MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE2lFRaRRzJx0Tspr8nT526HTY0LCQ&#10;g8WJhXI+LNXvmjbYokHMfZzM9xerR/u+Y/q1VK9NSH2cbXfGAmT24gC4DQ&#61;&#61;&#10;-----END PUBLIC KEY-----&#10;</pubkeyjwt>
             </publickeys>
           </datendesantragstellers>
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
              private static final Random badRandom = new Random(0xBAD);

              @Override
              public void nextBytes(byte[] bytes) {
                badRandom.nextBytes(bytes);
              }
            })
        .generate();
  }
}
