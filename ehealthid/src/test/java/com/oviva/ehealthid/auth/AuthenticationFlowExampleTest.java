package com.oviva.ehealthid.auth;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.oviva.ehealthid.auth.AuthenticationFlow.Session;
import com.oviva.ehealthid.fedclient.FederationMasterClientImpl;
import com.oviva.ehealthid.fedclient.api.CachedFederationApiClient;
import com.oviva.ehealthid.fedclient.api.FederationApiClientImpl;
import com.oviva.ehealthid.fedclient.api.InMemoryCacheImpl;
import com.oviva.ehealthid.fedclient.api.JavaHttpClient;
import com.oviva.ehealthid.fedclient.api.OpenIdClient;
import com.oviva.ehealthid.fedclient.api.UrlFormBodyBuilder;
import com.oviva.ehealthid.test.Environment;
import com.oviva.ehealthid.test.GematikHeaderDecoratorHttpClient;
import com.oviva.ehealthid.util.JwksUtils;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class AuthenticationFlowExampleTest {

  @Test
  @Disabled("e2e")
  void flowIntegrationTest() throws IOException, InterruptedException {

    // setup your environment, your own issuer MUST serve a _valid_ and _trusted_ entity
    // configuration
    // see: https://wiki.gematik.de/pages/viewpage.action?pageId=544316583
    var fedmaster = URI.create("https://app-test.federationmaster.de");
    var self = URI.create("https://idp-test.oviva.io/auth/realms/master/ehealthid");

    // this URI must be listed in your entity statement, configure as needed
    var redirectUri = URI.create("http://localhost:8080");

    // those _MUST_ be at most the ones you requested when handing in the entity statement
    var scopes = List.of("openid", "urn:telematik:email", "urn:telematik:versicherter");

    // path to the JWKS containing the private keys to decrypt ID tokens, the public part
    // is in your entity configuration
    var relyingPartyEncryptionJwks = JwksUtils.load(Path.of("../relying-party-enc_jwks.json"));

    // setup the file `.env.properties` to provide the X-Authorization header for the Gematik
    // test environment
    // see: https://wiki.gematik.de/display/IDPKB/Fachdienste+Test-Umgebungen
    var httpClient =
        new GematikHeaderDecoratorHttpClient(new JavaHttpClient(HttpClient.newHttpClient()));

    // setup as needed
    var clock = Clock.systemUTC();
    var ttl = Duration.ofMinutes(5);

    var federationApiClient =
        new CachedFederationApiClient(
            new FederationApiClientImpl(httpClient),
            new InMemoryCacheImpl<>(clock, ttl),
            new InMemoryCacheImpl<>(clock, ttl),
            new InMemoryCacheImpl<>(clock, ttl));

    var fedmasterClient = new FederationMasterClientImpl(fedmaster, federationApiClient, clock);
    var openIdClient = new OpenIdClient(httpClient);

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

  private Map<String, String> parseQuery(String uri) {
    var u = URI.create(uri);
    var raw = u.getQuery();

    return Arrays.stream(raw.split("&"))
        .map(p -> Arrays.asList(p.split("=")))
        .map(
            splits ->
                splits.stream().map(s -> URLDecoder.decode(s, StandardCharsets.UTF_8)).toList())
        .collect(Collectors.toMap(s -> s.get(0), s -> s.get(1)));
  }

  private String doFederatedAuthFlow(URI parUri) throws IOException, InterruptedException {

    var client = HttpClient.newHttpClient();

    var doc =
        Jsoup.connect(parUri.toString())
            .header("X-Authorization", Environment.gematikAuthHeader())
            .get();

    var form = doc.forms().stream().findFirst().orElseThrow();
    var action = form.absUrl("action");

    var formBuilder = UrlFormBodyBuilder.create();
    form.formData().forEach(k -> formBuilder.param(k.key(), k.value()));
    var urlEncoded = formBuilder.build();

    var formSubmit = action + "?" + new String(urlEncoded, StandardCharsets.UTF_8);

    var req =
        HttpRequest.newBuilder()
            .GET()
            .header("X-Authorization", Environment.gematikAuthHeader())
            .uri(URI.create(formSubmit))
            .build();

    var res = client.send(req, BodyHandlers.ofString());
    assertEquals(302, res.statusCode(), "response was:\n%s".formatted(res));

    return res.headers().firstValue("location").orElseThrow();
  }

  private String calculateS256CodeChallenge(String codeVerifier) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      var hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  private String generateCodeVerifier() {
    var rng = new SecureRandom();

    var bytes = new byte[32];
    rng.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
