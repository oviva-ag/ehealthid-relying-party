package com.oviva.ehealthid.fedclient;

import java.net.URI;

public class FederationExceptions {

  private FederationExceptions() {}

  public static FederationException badEntityStatement(Exception cause) {
    return new FederationException("failed to parse entity statement", cause);
  }

  public static FederationException badIdpList(Exception cause) {
    return new FederationException("failed to parse idp list", cause);
  }

  public static FederationException notAnIdpList(String actualType) {
    return new FederationException(
        "JWS is not of type idp-list but rather '%s'".formatted(actualType));
  }

  public static FederationException emptyIdpList(URI master) {
    return new FederationException("list of idps empty from '%s'".formatted(master));
  }

  public static FederationException badSignature(Exception cause) {
    return new FederationException("bad signature", cause);
  }

  public static FederationException notAnEntityStatement(String actualType) {
    return new FederationException(
        "JWS is not of type entity statement but rather '%s'".formatted(actualType));
  }

  public static FederationException entityStatementMissingFederationFetchUrl(String sub) {
    return new FederationException(
        "entity statement of '%s' has no federation fetch url".formatted(sub));
  }

  public static FederationException entityStatementTimeNotValid(String sub) {
    return new FederationException(
        "entity statement of '%s' expired or not yet valid".formatted(sub));
  }

  public static FederationException entityStatementBadSignature(String sub) {
    return new FederationException("entity statement of '%s' has a bad signature".formatted(sub));
  }

  public static FederationException federationStatementTimeNotValid(String sub) {
    return new FederationException(
        "federation statement of '%s' expired or not yet valid".formatted(sub));
  }

  public static FederationException federationStatementBadSignature(String sub) {
    return new FederationException(
        "federation statement of '%s' has a bad signature".formatted(sub));
  }

  public static FederationException untrustedFederationStatement(String sub) {
    return new FederationException("federation statement untrusted: sub=%s".formatted(sub));
  }
}
