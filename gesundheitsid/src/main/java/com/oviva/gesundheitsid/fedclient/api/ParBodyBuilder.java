package com.oviva.gesundheitsid.fedclient.api;

import java.net.URI;
import java.util.List;

public class ParBodyBuilder {

  private final UrlFormBodyBuilder form = UrlFormBodyBuilder.create();

  protected ParBodyBuilder() {}

  public static ParBodyBuilder create() {
    return new ParBodyBuilder();
  }

  public ParBodyBuilder responseType(String t) {
    form.param("response_type", t);
    return this;
  }

  public ParBodyBuilder codeChallenge(String cc) {
    form.param("code_challenge", cc);
    return this;
  }

  public ParBodyBuilder codeChallengeMethod(String method) {
    form.param("code_challenge_method", method);
    return this;
  }

  public ParBodyBuilder scopes(List<String> scopes) {
    var v = String.join(" ", scopes);
    form.param("scope", v);
    return this;
  }

  public ParBodyBuilder state(String state) {
    form.param("state", state);
    return this;
  }

  public ParBodyBuilder nonce(String nonce) {
    form.param("nonce", nonce);
    return this;
  }

  public ParBodyBuilder clientId(String clientId) {
    form.param("client_id", clientId);
    return this;
  }

  public ParBodyBuilder redirectUri(URI redirect) {
    form.param("redirect_uri", redirect.toString());
    return this;
  }

  // "gematik-ehealth-loa-high" or "gematik-ehealth-loa-substancial"
  public ParBodyBuilder acrValues(String acrValues) {
    form.param("acr_values", acrValues);
    return this;
  }

  public byte[] build() {
    return form.build();
  }
}
