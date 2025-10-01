package com.oviva.ehealthid.relyingparty.ws;

import static com.oviva.ehealthid.relyingparty.svc.LocalizedException.Message;
import static com.oviva.ehealthid.relyingparty.util.LocaleUtils.getNegotiatedLocale;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oviva.ehealthid.relyingparty.svc.AuthService;
import com.oviva.ehealthid.relyingparty.svc.AuthService.AuthorizationRequest;
import com.oviva.ehealthid.relyingparty.svc.AuthService.CallbackRequest;
import com.oviva.ehealthid.relyingparty.svc.AuthService.SelectedIdpRequest;
import com.oviva.ehealthid.relyingparty.svc.ValidationException;
import com.oviva.ehealthid.relyingparty.ws.ui.Pages;
import com.oviva.ehealthid.relyingparty.ws.ui.TemplateRenderer;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.NewCookie.SameSite;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

@Path("/auth")
public class AuthEndpoint {
  private final Pages pages = new Pages(new TemplateRenderer());

  private final AuthService authService;

  public AuthEndpoint(AuthService authService) {
    this.authService = authService;
  }

  // Authorization Request
  // https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.1
  @GET
  @Produces(MediaType.TEXT_HTML)
  public Response auth(
      @QueryParam("scope") String scope,
      @QueryParam("state") String state,
      @QueryParam("response_type") String responseType,
      @QueryParam("client_id") String clientId,
      @QueryParam("redirect_uri") String redirectUri,
      @QueryParam("app_uri") String appUriStr,
      @QueryParam("nonce") String nonce,
      @HeaderParam("Accept-Language") @DefaultValue("de-DE") String acceptLanguage) {

    var uri = mustParse(redirectUri);
    var appUri = parseUri(appUriStr);
    var req = new AuthorizationRequest(scope, state, responseType, clientId, uri, appUri, nonce);
    var res = authService.auth(req);

    var locale = getNegotiatedLocale(acceptLanguage);
    var form = pages.selectIdpForm(res.identityProviders(), appUri, locale);

    return Response.ok(form, MediaType.TEXT_HTML_TYPE)
        .cookie(createSessionCookie(res.sessionId()))
        .build();
  }

  @NonNull
  private URI mustParse(@Nullable String uri) {
    if (uri == null || uri.isBlank()) {
      var localizedMessage = new Message("error.blankUri");
      throw new ValidationException(localizedMessage);
    }
    try {
      return new URI(uri);
    } catch (URISyntaxException e) {
      var localizedMessage = new Message("error.badUri", uri);
      throw new ValidationException(localizedMessage);
    }
  }

  @Nullable
  private URI parseUri(@Nullable String uri) {
    if (uri == null || uri.isBlank()) {
      return null;
    }

    try {
      return new URI(uri);
    } catch (URISyntaxException e) {
      var localizedMessage = new Message("error.badUri", uri);
      throw new ValidationException(localizedMessage);
    }
  }

  private NewCookie createSessionCookie(String sessionId) {
    return new NewCookie.Builder("session_id")
        .value(sessionId)
        .secure(true)
        .httpOnly(true)
        .sameSite(SameSite.LAX)
        .maxAge(-1) // session scoped
        .path("/auth")
        .build();
  }

  @POST
  @Path("/select-idp")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.TEXT_HTML)
  public Response postSelectIdp(
      @CookieParam("session_id") String sessionId,
      @FormParam("identityProvider") String identityProvider,
      @HeaderParam("Accept-Language") @DefaultValue("de-DE") String acceptLanguage) {

    var redirect =
        authService.selectedIdentityProvider(new SelectedIdpRequest(sessionId, identityProvider));

    var locale = getNegotiatedLocale(acceptLanguage);
    var page = pages.jumpToApp(redirect, locale);

    return Response.ok(page, MediaType.TEXT_HTML_TYPE).build();
  }

  @GET
  @Path("/callback")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.TEXT_HTML)
  public Response callback(
      @CookieParam("session_id") String sessionId,
      @QueryParam("code") String code,
      @HeaderParam("Accept-Language") @DefaultValue("de-DE") String acceptLanguage) {

    var redirectUri = authService.callback(new CallbackRequest(sessionId, code));
    var locale = getNegotiatedLocale(acceptLanguage);
    var successPage = pages.success(redirectUri, locale);

    return Response.ok(successPage, MediaType.TEXT_HTML_TYPE).build();
  }

  public record AuthResponse(@JsonProperty("identity_providers") List<IdpEntry> identityProviders) {

    public record IdpEntry(
        @JsonProperty("iss") String iss,
        @JsonProperty("name") String name,
        @JsonProperty("logo_uri") String logoUrl) {}
  }
}
