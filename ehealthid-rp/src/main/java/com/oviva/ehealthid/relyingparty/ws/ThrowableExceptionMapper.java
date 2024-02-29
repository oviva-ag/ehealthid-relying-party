package com.oviva.ehealthid.relyingparty.ws;

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
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

public class ThrowableExceptionMapper implements ExceptionMapper<Throwable> {

  private static final String SERVER_ERROR_MESSAGE =
      "Ohh no! Unexpected server error. Please try again.";
  private final Pages pages = new Pages(new TemplateRenderer());
  @Context UriInfo uriInfo;
  @Context Request request;
  @Context HttpHeaders headers;

  // Note: MUST be non-final for mocking
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

      return buildContentNegotiatedErrorResponse(ve.getMessage(), Status.BAD_REQUEST);
    }

    log(exception);

    var status = determineStatus(exception);

    return buildContentNegotiatedErrorResponse(SERVER_ERROR_MESSAGE, status);
  }

  private Response buildContentNegotiatedErrorResponse(String message, StatusType status) {

    if (acceptsTextHtml()) {
      var body = pages.error(message);
      return Response.status(status).entity(body).type(MediaType.TEXT_HTML_TYPE).build();
    }

    return Response.status(status).build();
  }

  private boolean acceptsTextHtml() {
    var acceptable = headers.getAcceptableMediaTypes();
    return acceptable.contains(MediaType.WILDCARD_TYPE)
        || acceptable.contains(MediaType.TEXT_HTML_TYPE);
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
}
