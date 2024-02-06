package com.oviva.ehealthid.fedclient.api;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParBodyBuilderTest {

  @Test
  void build() {

    var body =
        ParBodyBuilder.create()
            .acrValues("very-very-high")
            .codeChallengeMethod("S256")
            .codeChallenge("myChallenge")
            .responseType("authorization_code")
            .redirectUri(URI.create("https://example.com/callback"))
            .state("#/myaccount")
            .nonce("bcff66cb-4f01-4129-82a9-0e27703db958")
            .scopes(List.of("email", "openid"))
            .clientId("https://fachdienst.example.com/auth/realms/main")
            .build();

    var asString = new String(body, StandardCharsets.UTF_8);

    assertEquals(
        "acr_values=very-very-high&code_challenge_method=S256&code_challenge=myChallenge&response_type=authorization_code&redirect_uri=https%3A%2F%2Fexample.com%2Fcallback&state=%23%2Fmyaccount&nonce=bcff66cb-4f01-4129-82a9-0e27703db958&scope=email+openid&client_id=https%3A%2F%2Ffachdienst.example.com%2Fauth%2Frealms%2Fmain",
        asString);
  }
}
