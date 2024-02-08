[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=oviva-ag_ehealthid-relying-party&metric=alert_status&token=ee904c8acea811b217358c63297ebe91fd6aee14)](https://sonarcloud.io/summary/new_code?id=oviva-ag_ehealthid-relying-party)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=oviva-ag_ehealthid-relying-party&metric=coverage&token=ee904c8acea811b217358c63297ebe91fd6aee14)](https://sonarcloud.io/summary/new_code?id=oviva-ag_ehealthid-relying-party)

# TODO
In order of priority:
- [ ] Continuous Deployment
- [ ] Helm chart (externally)
- [ ] Internationalization (ResourceBundles) for templates (en & de)
  - see [Mustache Library](https://github.com/spullara/mustache.java/blob/main/compiler/src/main/java/com/github/mustachejava/functions/BundleFunctions.java)
- [ ] Metrics endpoint
    - in-memory store sizes (entries of SessionRepo and CodeRepo)
    - count of flows with their result

# OpenID Connect Relying Party for GesundheitsID (eHealthID)

The goal is to provide a simple standalone server exposing Germany's 'GesundheitsID' (eHealthID) as
a good old OpenID Connect Relying Party (OIDC RP).

Identity Providers such as Keycloak can link accounts with OIDC out-of-the-box

## Contents

- [ehealthid-rp](./ehealthid-rp) - A standalone application to act as a OpenID Connect (OIDC)
  Relying Party. Bridges OIDC and Germany's GesundheitsID OpenID federation.
- [esgen](./esgen) - A script to generate keys and federation registration forms.
- [ehealthid](./ehealthid) - A plain Java library to build RelyingParties for GesundheitsID.
    - API clients
    - Models for the EntityStatments, IDP list endpoints etc.
    - Narrow support for the 'Fachdienst' use-case.

## Limitations

- for now sessions are stored in-memory, this implies:
    - rebooting the server will force users currently logging-in to restart
    - if multiple instances run, sessions must be sticky (e.g. use `session_id` cookie)
    - though it would be relatively straight forward to use a database instead
- this is tested in the 'Testumgebung' against the Gematik IDP due to a lack of other options

# Quickstart

```shell
# build everything
./mvnw clean verify

# generate keys for the application, keep those safe
./gen_keys.sh \
    --issuer-uri=https://mydiga.example.com \
    --member-id="$MEMBER_ID" \
    --organisation-name="My DiGA" \
    --generate-keys
    
# configure the application
export EHEALTHID_RP_APP_NAME=Awesome DiGA
export EHEALTHID_RP_BASE_URI=https://mydiga.example.com
export EHEALTHID_RP_FEDERATION_ENC_JWKS_PATH=enc_jwks.json
export EHEALTHID_RP_FEDERATION_MASTER=https://app-test.federationmaster.de
export EHEALTHID_RP_FEDERATION_SIG_JWKS_PATH=sig_jwks.json
export EHEALTHID_RP_REDIRECT_URIS=https://sso-mydiga.example.com/auth/callback
export EHEALTHID_RP_ES_TTL=PT5M

# starts the relying party server
./start.sh

# send in the generated XML to Gematik in order to register your IDP
cat federation_registration_form.xml
```

Once the server is booted, it will:

1. Expose an OpenID Discovery document at `$EHEALTHID_RP_BASE_URI/.well-known/openid-configuration`
   ```shell
    curl $BASE_URI/.well-known/openid-configuration | jq .
    ```

2. Expose an OpenID Federation entity configuration
   at `$EHEALTHID_RP_BASE_URI/.well-known/openid-federation`
   ```shell
    curl $BASE_URI/.well-known/openid-federation | jwt decode -j - | jq .payload
    ```
   **IMPORTANT:** Once the entity configuration is reachable in the internet it can be registered
   with Gematik. You can directly send in the XML generated in the second step, the file is
   called `federation_registration_form.xml`. See documentation further below.

3. Be ready to handle OpenID Connect flows and handle them via Germany's GesundheitsID federation.

The discovery document can be used to configure the relying party in an existing identity provider.

# Configuration

Use environment variables to configure the relying party server.

| Name                                     | Description                                                                                                                                      | Example                                                 |
|------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------|
| `EHEALTHID_RP_FEDERATION_ENC_JWKS_PATH`  | Path to a JWKS with at least one keypair for encryption of ID tokens.                                                                            | `./enc_jwks.json`                                       |
| `EHEALTHID_RP_FEDERATION_SIG_JWKS_PATH`  | Path to a JWKS with at least one keypair for signature withing the federation. All these keys __MUST__ be registered with the federation master. | `./sig_jwks.json`                                       |
| `EHEALTHID_RP_REDIRECT_URIS`             | Valid redirection URIs for OpenID connect.                                                                                                       | `https://sso-mydiga.example.com/auth/callback`          |
| `EHEALTHID_RP_BASE_URI`                  | The external base URI of the relying party. This is also the `issuer` towards the OpenID federation. Additional paths are unsupported for now.   | `https://mydiga-rp.example.com`                         |
| `EHEALTHID_RP_HOST`                      | Host to bind to.                                                                                                                                 | `0.0.0.0`                                               |
| `EHEALTHID_RP_PORT`                      | Port to bind to.                                                                                                                                 | `1234`                                                  |
| `EHEALTHID_RP_FEDERATION_MASTER`         | The URI of the federation master.                                                                                                                | `https://app-test.federationmaster.de`                  |
| `EHEALTHID_RP_APP_NAME`                  | The application name within the federation.                                                                                                      | `Awesome DiGA`                                          |
| `EHEALTHID_RP_ES_TTL`                    | The time to live for the entity statement. In ISO8601 format.                                                                                    | `PT12H`                                                 |
| `EHEALTHID_RP_SCOPES`                    | The comma separated list of scopes requested in the federation. This __MUST__ match what was registered with the federation master.              | `openid,urn:telematik:email,urn:telematik:display_name` |
| `EHEALTHID_RP_SESSION_STORE_TTL`         | The time to live for sessions. In ISO8601 format.                                                                                                | `PT20M`                                                 |
| `EHEALTHID_RP_SESSION_STORE_MAX_ENTRIES` | The maximum number of sessions to store. Keeps memory bounded.                                                                                   | `1000`                                                  |
| `EHEALTHID_RP_CODE_STORE_TTL`            | The time to live for codes, i.e. successful logins where the code is not redeemed yet. In ISO8601 format.                                        | `PT5M`                                                  |
| `EHEALTHID_RP_CODE_STORE_MAX_ENTRIES`    | The maximum number of codes to store. Keeps memory bounded.                                                                                      | `1000`                                                  |

# Generate Keys & Register for Federation

In order to participate in the GesundheitsID one needs to register the entity statement of the IDP
or in this case the relying party here.

To simplify matter, here a script to generate fresh keys as well as the XML necessary to register
with Gematik.

See [Gematik documentation](https://wiki.gematik.de/pages/viewpage.action?pageId=544316583) for
details
on the registration process.

```shell
./gen_keys.sh --help
```

### Generate Fresh Keys and Prepare Registration

```shell
# a string received from Gematik as part of the registration process
export MEMBER_ID=FDmyDiGa0112TU

./gen_keys.sh \
    --issuer-uri=https://mydiga.example.com \
    --member-id="$MEMBER_ID" \
    --organisation-name="My DiGA" \
    --generate-keys
    
# send in the generated XML to Gematik
cat federation_registration_form.xml
```

### Re-use Existing Keys and Prepare Registration

```shell
# a string received from Gematik as part of the registration process
export MEMBER_ID=FDmyDiGa0112TU

# specify the environment, either 
# TU -> test environment
# RU -> reference environment
# PU -> productive environment
export ENVIRONMENT=RU

./gen_keys.sh \
    --issuer-uri=https://mydiga.example.com \
    --member-id="$MEMBER_ID" \
    --organisation-name="My DiGA" \
    --environment=$ENVIRONMENT \
    --signing-jwks=./sig_jwks.json \
    --encryption-jwks=./enc_jwks.json
    
# send in the generated XML to Gematik
cat federation_registration_form.xml
```

## Library IntegrationTest flow with Gematik Reference IDP

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

## Working with Gematik Test Environment

### Gematik Test Sektoraler IdP in Browser

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

## Setup Test VM

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

## Helpful Links

- [Gematik Sectoral IDP Specifications v2.0.1](https://fachportal.gematik.de/fachportal-import/files/gemSpec_IDP_Sek_V2.0.1.pdf)
- [AppFlow - Authentication flow to implement](https://wiki.gematik.de/display/IDPKB/App-App+Flow#AppAppFlow-0-FederationMaster)
- [Sektoraler IDP - Examples & Reference Implementation](https://wiki.gematik.de/display/IDPKB/Sektoraler+IDP+-+Referenzimplementierung+und+Beispiele)
- [OpenID Federation Spec](https://openid.net/specs/openid-federation-1_0.html)
