package com.oviva.ehealthid.fedclient;

import java.net.URI;

public class FederationExceptions {

  private FederationExceptions() {}

  public static FederationException badEntityStatement(Exception cause) {
    return new FederationException(
        "failed to parse entity statement",
        cause,
        FederationException.Reason.INVALID_ENTITY_STATEMENT);
  }

  public static FederationException badIdpList(Exception cause) {
    return new FederationException(
        "failed to parse idp list", cause, FederationException.Reason.BAD_FEDERATION_MASTER);
  }

  public static FederationException notAnIdpList(String actualType) {
    return new FederationException(
        "JWS is not of type idp-list but rather '%s'".formatted(actualType),
        FederationException.Reason.BAD_FEDERATION_MASTER);
  }

  public static FederationException emptyIdpList(URI master) {
    return new FederationException(
        "list of idps empty from '%s'".formatted(master),
        FederationException.Reason.BAD_FEDERATION_MASTER);
  }

  public static FederationException badSignature(Exception cause) {
    return new FederationException(
        "bad signature", cause, FederationException.Reason.INVALID_ENTITY_STATEMENT);
  }

  public static FederationException notAnEntityStatement(String actualType) {
    return new FederationException(
        "JWS is not of type entity statement but rather '%s'".formatted(actualType),
        FederationException.Reason.INVALID_ENTITY_STATEMENT);
  }

  public static FederationException entityStatementMissingFederationFetchUrl(String sub) {
    return new FederationException(
        "entity statement of '%s' has no federation fetch url".formatted(sub),
        FederationException.Reason.INVALID_ENTITY_STATEMENT);
  }

  public static FederationException entityStatementTimeNotValid(String sub) {
    return new FederationException(
        "entity statement of '%s' expired or not yet valid".formatted(sub),
        FederationException.Reason.UNTRUSTED_IDP);
  }

  public static FederationException entityStatementBadSignature(String sub) {
    return new FederationException(
        "entity statement of '%s' has a bad signature".formatted(sub),
        FederationException.Reason.UNTRUSTED_IDP);
  }

  public static FederationException federationStatementTimeNotValid(String sub) {
    return new FederationException(
        "federation statement of '%s' expired or not yet valid".formatted(sub),
        FederationException.Reason.UNTRUSTED_IDP);
  }

  public static FederationException federationStatementBadSignature(String sub) {
    return new FederationException(
        "federation statement of '%s' has a bad signature".formatted(sub),
        FederationException.Reason.UNTRUSTED_IDP);
  }

  public static FederationException untrustedFederationStatement(String sub) {
    return new FederationException(
        "federation statement untrusted: sub=%s".formatted(sub),
        FederationException.Reason.UNTRUSTED_IDP);
  }
}
