package com.oviva.ehealthid.relyingparty.ws;

import static com.oviva.ehealthid.relyingparty.svc.ValidationException.*;

import com.fasterxml.jackson.annotation.JsonProperty;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.jboss.resteasy.util.MediaTypeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

public class ThrowableExceptionMapper implements ExceptionMapper<Throwable> {

  private static final String SERVER_ERROR_MESSAGE = "error.serverError";
  private final Pages pages = new Pages(new TemplateRenderer());
  @Context UriInfo uriInfo;
  @Context Request request;
  @Context HttpHeaders headers;

  // Note: below fields MUST be non-final for mocking
  private MediaTypeNegotiator mediaTypeNegotiator = new ResteasyMediaTypeNegotiator();

  private Logger logger = LoggerFactory.getLogger(ThrowableExceptionMapper.class);

  @Override
  public Response toResponse(Throwable exception) {
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

    log(exception);

    var status = determineStatus(exception);

    var errorMessage = new Message(SERVER_ERROR_MESSAGE, (String) null);
    return buildContentNegotiatedErrorResponse(errorMessage, status);
  }

  private Response buildContentNegotiatedErrorResponse(Message message, StatusType status) {

    var headerString = headers.getHeaderString("Accept-Language");

    var mediaType =
        mediaTypeNegotiator.bestMatch(
            headers.getAcceptableMediaTypes(),
            List.of(MediaType.TEXT_HTML_TYPE, MediaType.APPLICATION_JSON_TYPE));

    if (MediaType.TEXT_HTML_TYPE.equals(mediaType)) {
      var body = pages.error(message, headerString);
      return Response.status(status).entity(body).type(MediaType.TEXT_HTML_TYPE).build();
    }

    if (MediaType.APPLICATION_JSON_TYPE.equals(mediaType)) {
      var body = new Problem("/server_error", message.messageKey());
      return Response.status(status).entity(body).type(MediaType.APPLICATION_JSON_TYPE).build();
    }

    return Response.status(status).build();
  }

  private StatusType determineStatus(Throwable exception) {
    if (exception instanceof WebApplicationException w) {
      var res = w.getResponse();
      if (res != null) {
        return res.getStatusInfo();
      }
    }
    return Status.INTERNAL_SERVER_ERROR;
  }

  private void log(Throwable exception) {

    var builder = logger.atError().setCause(exception);
    builder = addRequestContext(builder);
    builder = addTraceInfo(builder);
    builder = addServiceContext(builder);
    builder.log("unexpected exception: {}", exception.getMessage());
  }

  private LoggingEventBuilder addServiceContext(LoggingEventBuilder builder) {
    var title = ThrowableExceptionMapper.class.getPackage().getImplementationTitle();
    var version = ThrowableExceptionMapper.class.getPackage().getImplementationVersion();
    if (title == null || version == null) {
      return builder;
    }

    return builder.addKeyValue("serviceContext", Map.of("service", title, "version", version));
  }

  private LoggingEventBuilder addRequestContext(LoggingEventBuilder builder) {

    Map<String, String> map = new HashMap<>();

    map.put("requestUrl", uriInfo.getRequestUri().toString());
    map.put("requestMethod", request.getMethod());

    var userAgent = headers.getHeaderString("user-agent");
    if (userAgent != null) {
      map.put("userAgent", userAgent);
    }

    return builder.addKeyValue("httpRequest", map);
  }

  private LoggingEventBuilder addTraceInfo(LoggingEventBuilder log) {
    var traceparent = headers.getHeaderString("traceparent");
    if (traceparent == null) {
      return log;
    }

    var parsed = Traceparent.parse(traceparent);
    if (parsed == null) {
      return log;
    }

    return log.addKeyValue("traceId", parsed.traceId()).addKeyValue("spanId", parsed.spanId());
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

  private record Traceparent(String spanId, String traceId) {

    // https://www.w3.org/TR/trace-context/#traceparent-header-field-values
    public static final Pattern TRACEPARENT_PATTERN =
        Pattern.compile("^00-([a-f0-9]{32})-([a-f0-9]{16})-[a-f0-9]{2}$");

    static Traceparent parse(String s) {
      if (s == null || s.isBlank()) {
        return null;
      }
      s = s.trim();

      var m = TRACEPARENT_PATTERN.matcher(s);
      if (!m.matches()) {
        return null;
      }

      var traceId = m.group(1);
      var spanId = m.group(2);

      return new Traceparent(spanId, traceId);
    }
  }

  public record Problem(@JsonProperty("type") String type, @JsonProperty("title") String title) {}
}
