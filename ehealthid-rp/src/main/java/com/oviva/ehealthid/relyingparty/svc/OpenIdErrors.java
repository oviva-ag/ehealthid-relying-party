package com.oviva.ehealthid.relyingparty.svc;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import jakarta.ws.rs.core.UriBuilder;
import java.net.URI;

public class OpenIdErrors {
  private OpenIdErrors() {}

  public static URI redirectWithError(
      @NonNull URI redirectUri,
      @NonNull ErrorCode code,
      @Nullable String state,
      @Nullable String description) {
    var builder = UriBuilder.fromUri(redirectUri);

    builder.queryParam("error", code.error());
    addNonBlankQueryParam(builder, "error_description", description);
    addNonBlankQueryParam(builder, "state", state);

    return builder.build();
  }

  private static void addNonBlankQueryParam(UriBuilder builder, String name, String value) {
    if (value == null || value.isBlank()) {
      return;
    }
    builder.queryParam(name, value);
  }

  // https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2.1
  public enum ErrorCode {

    // The request is missing a required parameter, includes an invalid parameter value, includes a
    // parameter more than once, or is otherwise malformed.
    INVALID_REQUEST("invalid_request"),

    // The client is not authorized to request an authorization code using this method.
    UNAUTHORIZED_CLIENT("unauthorized_client"),

    // The resource owner or authorization server denied the request.
    ACCESS_DENIED("access_denied"),

    // The authorization server does not support obtaining an authorization code using this method.
    UNSUPPORTED_RESPONSE_TYPE("unsupported_response_type"),

    // The requested scope is invalid, unknown, or malformed.
    INVALID_SCOPE("invalid_scope"),

    // The authorization server encountered an unexpected condition that prevented it from
    // fulfilling the request. (This error code is needed because a 500 Internal Server Error HTTP
    // status code cannot be returned to the client via an HTTP redirect.)
    SERVER_ERROR("server_error"),

    // The authorization server is currently unable to handle the request due to a temporary
    // overloading or maintenance of the server.  (This error code is needed because a 503 Service
    // Unavailable HTTP status code cannot be returned to the client via an HTTP redirect.)
    TEMPORARILY_UNAVAILABLE("temporarily_unavailable");

    private final String error;

    ErrorCode(String error) {
      this.error = error;
    }

    public String error() {
      return error;
    }
  }
}
