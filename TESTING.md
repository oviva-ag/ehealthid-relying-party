
# Library IntegrationTest flow with Gematik Reference IDP

**Prerequisites**:

1. Setup your test environment, your own issuer **MUST** serve a **VALID** and **TRUSTED** entity
   statement. See [Gematik docs](https://wiki.gematik.de/pages/viewpage.action?pageId=544316583)
2. Setup the file `env.properties` to provide
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

See [AuthenticationFlowExampleTest](https://github.com/oviva-ag/ehealthid-relying-party/blob/main/ehealthid/src/test/java/com/oviva/ehealthid/auth/AuthenticationFlowExampleTest.java)

# Gematik Test Environment

## Gematik Test Sektoraler IdP in Browser

Since the Gematik reference IDP in the Test Environment needs a custom header, it can not be used
directly in the browser for authentication.
Setting up a proxy with a header filter can get around that limitation though.

**Prerequisite:** Install some Chrome-ish browser
like [Thorium](https://github.com/Alex313031/Thorium-MacOS/releases) or Chromium.

1. launch `mitmweb`
    ```
    mitmweb -p 8881 --web-port=8882 --set "modify_headers=/~q & ~d gsi.dev.gematik.solutions/X-Authorization/<value goes here>"
    ```

2. launch a Chrome-like browser
    ```
    /Applications/Thorium.app/Contents/MacOS/Thorium --proxy-server=http://localhost:8881
    ```

# Setup Test VM

For testing the entity statement of the relying party must be publicly available via HTTPS. Setting
up a quick VM
with a caddy reverse proxy makes that easy.

```shell

# adapt as necessary, make sure to set up the corresponding DNS A records
DOMAIN=mydiga.example.com

sudo apt update
sudo apt install jq openjdk-17-jre-headless

# install caddy
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https curl
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update
sudo apt install caddy

# caddy enables itself by default, we don't want it
sudo systemctl disable --now caddy

sudo caddy reverse-proxy --from=$DOMAIN --to=:1234
```
