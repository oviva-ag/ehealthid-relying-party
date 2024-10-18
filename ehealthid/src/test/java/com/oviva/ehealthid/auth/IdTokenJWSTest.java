package com.oviva.ehealthid.auth;

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jose.Payload;
import com.oviva.ehealthid.util.JsonCodec;
import com.oviva.ehealthid.util.JsonPayloadTransformer;
import org.junit.jupiter.api.Test;

class IdTokenJWSTest {

  @Test
  void parse() {

    var sub =
        "LWhefF_EOPv8DaFqmMuQVJA1qOfAX8zX75QU0vhVq7_niWBiBe2fRl5acPPxTNb-2kbEZaCQTI8PswuIpKJftpMPHvyCdXlGwC6qE68ag3vdIFAnsHTG1IqB7NOKgydnlA";
    var idTokenPayload =
        """
                    {
                      "sub": "LWhefF_EOPv8DaFqmMuQVJA1qOfAX8zX75QU0vhVq7_niWBiBe2fRl5acPPxTNb-2kbEZaCQTI8PswuIpKJftpMPHvyCdXlGwC6qE68ag3vdIFAnsHTG1IqB7NOKgydnlA",
                      "urn:telematik:claims:id": "X123447974",
                      "urn:telematik:claims:organization": "101575519",
                      "amr": [
                        "urn:telematik:auth:other"
                      ],
                      "iss": "https://idbroker.tk.example.com",
                      "nonce": "H-124356jQstUSxNixvo9w",
                      "aud": "https://ehealthid.ovivadiga.com",
                      "acr": "gematik-ehealth-loa-high",
                      "urn:telematik:claims:profession": "1.2.276.0.76.4.49",
                      "auth_time": 1729249870,
                      "exp": 1729250171,
                      "iat": 1729249871
                    }
                    """;

    var raw = new Payload(idTokenPayload);
    var identity =
        raw.toType(new JsonPayloadTransformer<>(IdTokenJWS.IdToken.class, JsonCodec::readValue));

    assertEquals(identity.sub(), sub);
  }
}
