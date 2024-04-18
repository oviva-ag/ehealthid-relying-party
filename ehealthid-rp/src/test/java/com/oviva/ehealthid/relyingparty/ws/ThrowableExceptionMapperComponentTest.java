package com.oviva.ehealthid.relyingparty.ws;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.startsWith;

import com.oviva.ehealthid.relyingparty.test.EmbeddedRelyingParty;
import io.restassured.RestAssured;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ThrowableExceptionMapperComponentTest {

  private static EmbeddedRelyingParty app;

  @BeforeAll
  static void beforeAll() throws ExecutionException, InterruptedException {
    app = new EmbeddedRelyingParty();
    app.start();
    RestAssured.baseURI = app.baseUri().toString();
  }

  @AfterAll
  static void afterAll() throws Exception {
    app.close();
  }

  /** Regression test for: oviva-ag/ehealthid-relying-party #58 / EPA-102 */
  @Test
  void toResponse_htmlUnescaped() {

    given()
        .accept("text/html,*/*")
        .get("/auth")
        .then()
        .header("content-type", startsWith("text/html"))
        .body(startsWith("<!DOCTYPE html>"));
  }
}
