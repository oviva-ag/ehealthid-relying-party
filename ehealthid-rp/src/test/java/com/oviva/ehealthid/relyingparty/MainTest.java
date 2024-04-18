package com.oviva.ehealthid.relyingparty;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.oviva.ehealthid.relyingparty.test.EmbeddedRelyingParty;
import io.restassured.http.ContentType;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MainTest {

  private static final String DISCOVERY_PATH = "/.well-known/openid-configuration";
  private static final String FEDERATION_CONFIG_PATH = "/.well-known/openid-federation";
  private static final String JWKS_PATH = "/jwks.json";
  private static final String HEALTH_PATH = "/health";
  private static final String METRICS_PATH = "/metrics";
  private static final String AUTH_PATH = "/auth";
  private static final String IDP_PATH = "auth/select-idp";
  private static final String CALLBACK_PATH = "auth/callback";

  private static EmbeddedRelyingParty application;

  @RegisterExtension
  static WireMockExtension wm =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @BeforeAll
  static void beforeAll() throws ExecutionException, InterruptedException {
    application = new EmbeddedRelyingParty();
    application.start();
  }

  @AfterAll
  static void afterAll() throws Exception {
    application.close();
  }

  @Test
  void run_smokeTest() {

    var baseUri = application.baseUri();

    // then
    assertGetOk(baseUri.resolve(DISCOVERY_PATH));
    assertGetOk(baseUri.resolve(JWKS_PATH));
    assertGetOk(baseUri.resolve(FEDERATION_CONFIG_PATH));
    assertGetOk(baseUri.resolve(HEALTH_PATH));
    assertGetOk(baseUri.resolve(METRICS_PATH));
  }

  @Test
  void run_metrics() {

    var baseUri = application.baseUri();

    // when & then
    get(baseUri.resolve(METRICS_PATH))
        .then()
        .contentType(ContentType.TEXT)
        .body(containsString("cache_gets_total{cache=\"sessionCache\""))
        .body(containsString("cache_gets_total{cache=\"codeCache\""))
        .body(containsString("jvm_memory_used_bytes{area=\"heap\""))
        .body(containsString("jvm_gc_memory_allocated_bytes_total "));
  }

  @ParameterizedTest
  @MethodSource("provideInsecureRedirect")
  void run_auth_InsecureRedirect(String language, String redirectUri, String errorMessage) {
    var baseUri = application.baseUri();

    var scope = "openid";
    var state = UUID.randomUUID().toString();
    var nonce = UUID.randomUUID().toString();
    var responseType = "code";
    var clientId = "myapp";

    var response =
        given()
            .log()
            .all()
            .header("Accept-Language", language)
            .queryParam("scope", scope)
            .queryParam("nonce", nonce)
            .queryParam("response_type", responseType)
            .queryParam("client_Id", clientId)
            .queryParam("state", state)
            .queryParam("redirect_uri", redirectUri)
            .when()
            .get(baseUri.resolve(AUTH_PATH))
            .then()
            .contentType(ContentType.HTML)
            .statusCode(400)
            .extract()
            .response();

    var responseBody = response.getBody().asString();

    System.out.println(responseBody);
    assertTrue(responseBody.contains(language));
    assertTrue(responseBody.contains(errorMessage));
  }

  private static Stream<Arguments> provideInsecureRedirect() {
    return Stream.of(
        Arguments.of("en-US", "http://myapp.example.com", "Insecure redirect_uri"),
        Arguments.of("de-DE", "http://myapp.example.com", "Unsicherer redirect_uri"));
  }

  @ParameterizedTest
  @MethodSource("provideWrongScopeAndResponseType")
  void run_auth_withErrorRedirect(String scope, String responseType, String errorValue) {
    var baseUri = application.baseUri();
    var state = UUID.randomUUID().toString();
    var nonce = UUID.randomUUID().toString();
    var clientId = "myapp";

    var response =
        given()
            .log()
            .all()
            .header("Accept-Language", "en-US")
            .queryParam("scope", scope)
            .queryParam("nonce", nonce)
            .queryParam("response_type", responseType)
            .queryParam("client_id", clientId)
            .queryParam("state", state)
            .queryParam("redirect_uri", "https://myapp.example.com")
            .when()
            .redirects()
            .follow(false)
            .get(baseUri.resolve(AUTH_PATH))
            .then()
            .statusCode(303)
            .extract()
            .response();

    var locationHeader = response.getHeader("Location");
    assertTrue(locationHeader.contains("error.unsupportedScope"));
  }

  private static Stream<Arguments> provideWrongScopeAndResponseType() {
    return Stream.of(
        Arguments.of("wrongScope", "code", "error.unsupportedScope"),
        Arguments.of("openId", "wrongResponseType", "error.unsupportedResponseType"));
  }

  @ParameterizedTest
  @MethodSource("provideInvalidURIs")
  void run_auth_UriIssue(String language, String redirectUri, String errorMessage) {
    var baseUri = application.baseUri();

    var scope = "openid";
    var state = UUID.randomUUID().toString();
    var nonce = UUID.randomUUID().toString();
    var responseType = "code";
    var clientId = "myapp";

    var response =
        given()
            .log()
            .all()
            .header("Accept-Language", language)
            .queryParam("scope", scope)
            .queryParam("nonce", nonce)
            .queryParam("response_type", responseType)
            .queryParam("client_Id", clientId)
            .queryParam("state", state)
            .queryParam("redirect_uri", redirectUri)
            .when()
            .get(baseUri.resolve(AUTH_PATH))
            .then()
            .contentType(ContentType.HTML)
            .statusCode(400)
            .extract()
            .response();

    var responseBody = response.getBody().asString();
    assertTrue(responseBody.contains(language));
    assertTrue(responseBody.contains(errorMessage));
    assertTrue(responseBody.contains(redirectUri));
  }

  private static Stream<Arguments> provideInvalidURIs() {
    return Stream.of(
        Arguments.of("en-US", "not a valid URL", "Bad uri"),
        Arguments.of("de-DE", "not a valid URL", "Falsch uri"),
        Arguments.of("en-US", "", "Blank uri"),
        Arguments.of("de-DE", "", "Leere Uri"));
  }

  @ParameterizedTest
  @MethodSource("serverErrorLocalized")
  void run_auth(String language, int errorCode, String error) {
    var baseUri = application.baseUri();

    var scope = "openid";
    var state = UUID.randomUUID().toString();
    var nonce = UUID.randomUUID().toString();
    var responseType = "code";
    var clientId = "myapp";

    var response =
        given()
            .log()
            .all()
            .header("Accept-Language", language)
            .queryParam("scope", scope)
            .queryParam("nonce", nonce)
            .queryParam("responseType", responseType)
            .queryParam("client_Id", clientId)
            .queryParam("state", state)
            .queryParam("redirect_uri", "https://myapp.example.com")
            .when()
            .get(baseUri.resolve(AUTH_PATH))
            .then()
            .contentType(ContentType.HTML)
            .statusCode(errorCode)
            .extract()
            .response();

    var responseBody = response.getBody().asString();
    assertTrue(responseBody.contains(language));
    assertTrue(responseBody.contains(error));
  }

  private static Stream<Arguments> serverErrorLocalized() {
    return Stream.of(
        Arguments.of("en-US", 500, "Ohh no! Unexpected server error. Please try again."),
        Arguments.of(
            "de-DE", 500, "Ohh nein! Unerwarteter Serverfehler. Bitte versuchen Sie es erneut."));
  }

  @Test
  void run_callback() {
    var baseUri = application.baseUri();

    var sessionID = UUID.randomUUID().toString();

    var response =
        given()
            .log()
            .all()
            .cookie("session_id", sessionID)
            .formParam("code", "code")
            .when()
            .get(baseUri.resolve(CALLBACK_PATH))
            .then()
            .contentType(ContentType.HTML)
            .statusCode(400)
            .extract()
            .response();

    var responseBody = response.getBody().asString();
    assertTrue(responseBody.contains("de-DE"));
    assertTrue(
        responseBody.contains(
            "Oops, Sitzung unbekannt oder abgelaufen. Bitte starten Sie erneut."));
  }

  @Test
  void run_selectIdp() {
    var baseUri = application.baseUri();

    var sessionID = UUID.randomUUID().toString();

    var response =
        given()
            .log()
            .all()
            .cookie("session_id", sessionID)
            .formParam("identityProvider", "")
            .when()
            .post(baseUri.resolve(IDP_PATH))
            .then()
            .contentType(ContentType.HTML)
            .statusCode(400)
            .extract()
            .response();

    var responseBody = response.getBody().asString();
    assertTrue(responseBody.contains("de-DE"));
    assertTrue(responseBody.contains("Kein Identitätsanbieter ausgewählt. Bitte zurückgehen."));
  }

  private void assertGetOk(URI uri) {
    get(uri).then().statusCode(200);
  }
}
