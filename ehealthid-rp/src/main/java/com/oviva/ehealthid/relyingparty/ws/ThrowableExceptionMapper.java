package com.oviva.ehealthid.relyingparty.ws;

import static com.oviva.ehealthid.relyingparty.svc.ValidationException.*;
import static com.oviva.ehealthid.relyingparty.util.LocaleUtils.getNegotiatedLocale;

import com.oviva.ehealthid.auth.AuthException;
import com.oviva.ehealthid.fedclient.FederationException;
import com.oviva.ehealthid.relyingparty.svc.AuthenticationException;
import com.oviva.ehealthid.relyingparty.svc.ValidationException;
import com.oviva.ehealthid.relyingparty.ws.ui.Pages;
import com.oviva.ehealthid.relyingparty.ws.ui.TemplateRenderer;
import edu.umd.cs.findbugs.annotations.Nullable;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.Response.StatusType;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

public class ThrowableExceptionMapper implements ExceptionMapper<Throwable> {

  private static final String SERVER_ERROR_MESSAGE = "error.serverError";
  private static final String FEDERATION_ERROR_MESSAGE = "error.federationError";
  private static final String AUTH_ERROR_MESSAGE = "error.authError";

  private final Pages pages = new Pages(new TemplateRenderer());
  private final URI appUri;

  @Context UriInfo uriInfo;
  @Context Request request;
  @Context HttpHeaders headers;

  private Logger logger = LoggerFactory.getLogger(ThrowableExceptionMapper.class);

  public ThrowableExceptionMapper(@Nullable URI appUri) {
    this.appUri = appUri;
  }

  @Override
  public Response toResponse(Throwable exception) {

    debugLog(exception);

    if (exception instanceof WebApplicationException w) {
      var res = w.getResponse();
      if (res.getStatus() >= 500) {
        log(w);
      }
      return res;
    }

    if (exception instanceof AuthenticationException) {
      return Response.status(Status.UNAUTHORIZED).build();
    }

    if (exception instanceof ValidationException ve) {
      if (ve.seeOther() != null) {
        return Response.seeOther(ve.seeOther()).build();
      }

      return buildErrorResponse(ve.localizedMessage(), Status.BAD_REQUEST);
    }

    // the remaining exceptions are unexpected, let's log them
    log(exception);

    if (exception instanceof FederationException fe) {
      var errorMessage = new Message(FEDERATION_ERROR_MESSAGE, fe.reason().name());
      return buildErrorResponse(errorMessage, Status.INTERNAL_SERVER_ERROR);
    }

    if (exception instanceof AuthException ae) {
      var errorMessage = new Message(AUTH_ERROR_MESSAGE, ae.reason().name());
      return buildErrorResponse(errorMessage, Status.INTERNAL_SERVER_ERROR);
    }

    var status = Status.INTERNAL_SERVER_ERROR;

    var errorMessage = new Message(SERVER_ERROR_MESSAGE, (String) null);
    return buildErrorResponse(errorMessage, status);
  }

  private Response buildErrorResponse(Message message, StatusType status) {

    var headerString = headers.getHeaderString("Accept-Language");
    var locale = getNegotiatedLocale(headerString);

    var body = pages.error(message, appUri, locale);

    // FIXES oviva-ag/ehealthid-relying-party #58 / EPA-102
    // resteasy has a built-in `MessageSanitizerContainerResponseFilter` escaping all non status
    // 200
    // 'text/html' responses
    // if the entity is a string.
    // The corresponding "resteasy.disable.html.sanitizer" config does not work with SeBootstrap
    // currently (resteasy 6.2).
    return Response.status(status)
        .entity(body.getBytes(StandardCharsets.UTF_8))
        .type(MediaType.TEXT_HTML_TYPE)
        .build();
  }

  private void debugLog(Throwable exception) {
    if (logger.isDebugEnabled()) {
      logger.atDebug().setCause(exception).log("request failed: {}", exception.getMessage());
    }
  }

  private void log(Throwable exception) {

    var builder = logger.atError().setCause(exception);
    builder = addRequestContext(builder);
    builder.log("unexpected exception: {}", exception.getMessage());
  }

  private LoggingEventBuilder addRequestContext(LoggingEventBuilder builder) {

    // https://cloud.google.com/logging/docs/reference/v2/rest/v2/LogEntry#httprequest
    Map<String, String> map = new HashMap<>();

    map.put("requestUrl", uriInfo.getRequestUri().toString());
    map.put("requestMethod", request.getMethod());

    var userAgent = headers.getHeaderString("user-agent");
    if (userAgent != null) {
      map.put("userAgent", userAgent);
    }

    return builder.addKeyValue("httpRequest", map);
  }
}
