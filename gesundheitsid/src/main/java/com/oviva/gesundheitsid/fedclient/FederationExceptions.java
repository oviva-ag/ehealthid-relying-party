package com.oviva.gesundheitsid.fedclient;

import java.net.URI;

public class FederationExceptions {

  private FederationExceptions() {}

  public static RuntimeException badEntityStatement(Exception cause) {
    return new RuntimeException("failed to parse entity statement", cause);
  }

  public static RuntimeException badIdpList(Exception cause) {
    return new RuntimeException("failed to parse idp list", cause);
  }

  public static RuntimeException notAnIdpList(String actualType) {
    return new RuntimeException(
        "JWS is not of type idp-list but rather '%s'".formatted(actualType));
  }

  public static RuntimeException emptyIdpList(URI master) {
    return new RuntimeException("list of idps empty from '%s'".formatted(master));
  }

  public static RuntimeException badSignature(Exception cause) {
    return new RuntimeException("bad signature", cause);
  }

  public static RuntimeException notAnEntityStatement(String actualType) {
    return new RuntimeException(
        "JWS is not of type entity statement but rather '%s'".formatted(actualType));
  }

  public static RuntimeException failedToFetchValidEntityStatmentFor(URI issuer) {
    return new RuntimeException(
        "failed to fetch valid entity statement from '%s'".formatted(issuer));
  }

  public static RuntimeException entityStatementMissingFederationFetchUrl(String sub) {
    return new RuntimeException(
        "entity statement of '%s' has no federation fetch url".formatted(sub));
  }

  public static RuntimeException entityStatementTimeNotValid(String sub) {
    return new RuntimeException("entity statement of '%s' expired or not yet valid".formatted(sub));
  }

  public static RuntimeException entityStatementBadSignature(String sub) {
    return new RuntimeException("entity statement of '%s' has a bad signature".formatted(sub));
  }

  public static RuntimeException federationStatementTimeNotValid(String sub) {
    return new RuntimeException(
        "federation statement of '%s' expired or not yet valid".formatted(sub));
  }

  public static RuntimeException federationStatementBadSignature(String sub) {
    return new RuntimeException("federation statement of '%s' has a bad signature".formatted(sub));
  }

  public static RuntimeException untrustedFederationStatement(String sub) {
    return new RuntimeException("federation statement untrusted: sub=%s".formatted(sub));
  }
}
