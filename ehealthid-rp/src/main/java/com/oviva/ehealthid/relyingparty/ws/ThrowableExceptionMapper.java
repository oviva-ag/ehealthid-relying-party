package com.oviva.ehealthid.relyingparty.ws;

import static com.oviva.ehealthid.relyingparty.svc.ValidationException.*;
import static com.oviva.ehealthid.relyingparty.util.LocaleUtils.getNegotiatedLocale;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oviva.ehealthid.fedclient.FederationException;
import com.oviva.ehealthid.relyingparty.svc.AuthenticationException;
import com.oviva.ehealthid.relyingparty.svc.ValidationException;
import com.oviva.ehealthid.relyingparty.ws.ui.Pages;
import com.oviva.ehealthid.relyingparty.ws.ui.TemplateRenderer;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jboss.resteasy.util.MediaTypeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

public class ThrowableExceptionMapper implements ExceptionMapper<Throwable> {

  private static final String SERVER_ERROR_MESSAGE = "error.serverError";
  private static final String FEDERATION_ERROR_MESSAGE = "error.federationError";

  private final Pages pages = new Pages(new TemplateRenderer());
  @Context UriInfo uriInfo;
  @Context Request request;
  @Context HttpHeaders headers;

  // Note: below fields MUST be non-final for mocking
  private MediaTypeNegotiator mediaTypeNegotiator = new ResteasyMediaTypeNegotiator();

  private Logger logger = LoggerFactory.getLogger(ThrowableExceptionMapper.class);

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

      return buildContentNegotiatedErrorResponse(ve.localizedMessage(), Status.BAD_REQUEST);
    }

    // the remaining exceptions are unexpected, let's log them
    log(exception);

    if (exception instanceof FederationException fe) {
      var errorMessage = new Message(FEDERATION_ERROR_MESSAGE, fe.reason().name());
      return buildContentNegotiatedErrorResponse(errorMessage, Status.INTERNAL_SERVER_ERROR);
    }

    var status = Status.INTERNAL_SERVER_ERROR;

    var errorMessage = new Message(SERVER_ERROR_MESSAGE, (String) null);
    return buildContentNegotiatedErrorResponse(errorMessage, status);
  }

  private Response buildContentNegotiatedErrorResponse(Message message, StatusType status) {

    var headerString = headers.getHeaderString("Accept-Language");
    var locale = getNegotiatedLocale(headerString);

    var mediaType =
        mediaTypeNegotiator.bestMatch(
            headers.getAcceptableMediaTypes(),
            List.of(MediaType.TEXT_HTML_TYPE, MediaType.APPLICATION_JSON_TYPE));

    if (MediaType.TEXT_HTML_TYPE.equals(mediaType)) {
      var body = pages.error(message, locale);

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

    if (MediaType.APPLICATION_JSON_TYPE.equals(mediaType)) {
      var body = new Problem("/server_error", message.messageKey());
      return Response.status(status).entity(body).type(MediaType.APPLICATION_JSON_TYPE).build();
    }

    return Response.status(status).build();
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

  interface MediaTypeNegotiator {
    MediaType bestMatch(List<MediaType> desiredMediaType, List<MediaType> supportedMediaTypes);
  }

  private static class ResteasyMediaTypeNegotiator implements MediaTypeNegotiator {

    @Override
    public MediaType bestMatch(
        List<MediaType> desiredMediaType, List<MediaType> supportedMediaTypes) {

      // note: resteasy needs mutable lists
      return MediaTypeHelper.getBestMatch(
          new ArrayList<>(desiredMediaType), new ArrayList<>(supportedMediaTypes));
    }
  }

  public record Problem(@JsonProperty("type") String type, @JsonProperty("title") String title) {}
}
