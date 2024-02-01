[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=oviva-ag_keycloak-gesundheitsid&metric=alert_status&token=64c09371c0f6c1d729fc0b0424706cd54011cb90)](https://sonarcloud.io/summary/new_code?id=oviva-ag_keycloak-gesundheitsid)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=oviva-ag_keycloak-gesundheitsid&metric=coverage&token=64c09371c0f6c1d729fc0b0424706cd54011cb90)](https://sonarcloud.io/summary/new_code?id=oviva-ag_keycloak-gesundheitsid)

# Keycloak Identity Provider for GesundheitsID (eHealthID)

## Contents
- [gesundheitsid](./gesundheitsid) - A plain Java library to build RelyingParties for GesundheitsID.
  - API clients
  - Models for the EntityStatments, IDP list endpoints etc.
  - Narrow support for the 'Fachdienst' use-case.

## End-to-End Test flow with Gematik Reference IDP

**Prerequisites**:

1. Setup your test environment, your own issuer **MUST** serve a **VALID** and **TRUSTED** entity
  statement. See [Gematik docs](https://wiki.gematik.de/pages/viewpage.action?pageId=544316583)
2. Setup the file `.env.properties` to provide
  the [X-Authorization header](https://wiki.gematik.de/display/IDPKB/Fachdienste+Test-Umgebungen)
  for the Gematik
3. Setup the JWK sets for signing and encryption keys

```java
public class Example {

  public static void main(String[] args) {

    // ... setup, see full example linked below

    var flow =
        new AuthenticationFlow(
            self, fedmasterClient, openIdClient, relyingPartyEncryptionJwks::getKeyByKeyId);

    // these should come from the client in the real world
    var verifier = generateCodeVerifier();
    var codeChallenge = calculateS256CodeChallenge(verifier);

    // ==== 1) start a new flow
    var step1 = flow.start(new Session("test", "test", redirectUri, codeChallenge, scopes));

    // ==== 2) get the list of available IDPs
    var idps = step1.fetchIdpOptions();

    // ==== 3) select and IDP

    // for now we hardcode the reference IDP from Gematik
    var sektoralerIdpIss = "https://gsi.dev.gematik.solutions";

    var step2 = step1.redirectToSectoralIdp(sektoralerIdpIss);

    var idpRedirectUri = step2.idpRedirectUri();

    // ==== 3a) do in-code authentication flow, this is in reality the proprietary flow
    var redirectResult = doFederatedAuthFlow(idpRedirectUri);
    System.out.println(redirectResult);

    var values = parseQuery(redirectResult);
    var code = values.get("code");

    // ==== 4) exchange the code for the ID token
    var token = step2.exchangeSectoralIdpCode(code, verifier);

    // Success! Let's print it.
    var om = new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true);
    System.out.println(om.writeValueAsString(token.body()));
  }
}

```

See [AuthenticationFlowExampleTest](https://github.com/oviva-ag/keycloak-gesundheitsid/blob/8751c92e45531f6cdca204b8db18a2825e05e69a/gesundheitsid/src/test/java/com/oviva/gesundheitsid/auth/AuthenticationFlowExampleTest.java#L44-L117)

## Working with Gematik Test Environment


### Gematik Test Sektoraler IdP in Browser

Since the Gematik reference IDP in the Test Environment needs a custom header, it can not be used directly in the browser for authentication.
Setting up a proxy with a header filter can get around that limitation though.

**Prerequisite:** Install some Chrome-ish browser like [Thorium](https://github.com/Alex313031/Thorium-MacOS/releases) or Chromium.

1. launch `mitmweb`: `mitmweb -p 8881 --web-port=8882`
2. launch Chrome-like browser
    ```
    /Applications/Thorium.app/Contents/MacOS/Thorium --proxy-server=http://localhost:8881
   ```
3. setup `modify_headers` option
    ```mitmproxy
    # modify_headers filter 
    /~q & ~d gsi.dev.gematik.solutions/X-Authorization/<value goes here>
    ```


## Helpful Links

- [AppFlow - Authentication flow to implement](https://wiki.gematik.de/display/IDPKB/App-App+Flow#AppAppFlow-0-FederationMaster)
- [Sektoraler IDP - Examples & Reference Implementation](https://wiki.gematik.de/display/IDPKB/Sektoraler+IDP+-+Referenzimplementierung+und+Beispiele)
- [OpenID Federation Spec](https://openid.net/specs/openid-federation-1_0.html)

