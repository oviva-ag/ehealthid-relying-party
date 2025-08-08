package com.oviva.ehealthid.relyingparty.ws;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.bouncycastle.util.io.TeeInputStream;
import org.bouncycastle.util.io.TeeOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class RequestLogDumpProvider
    implements ContainerRequestFilter,
        ContainerResponseFilter,
        ReaderInterceptor,
        WriterInterceptor {

  private static final Logger log = LoggerFactory.getLogger("REQUEST_DUMP");

  private static final String REQUEST_TIME_PROPERTY = "requestTime";

  private static final String MDC_FIELD_DURATION = "duration";
  private static final String MDC_FIELD_HTTP_METHOD = "http_request_method";
  private static final String MDC_FIELD_REQUEST_BODY = "http_request_body";
  private static final String MDC_FIELD_REQUEST_HEADERS = "http_request_headers";
  private static final String MDC_FIELD_RESOURCE_CLASS = "resource_class";
  private static final String MDC_FIELD_RESOURCE_METHOD = "resource_method";
  private static final String MDC_FIELD_RESPONSE_BODY = "http_response_body";
  private static final String MDC_FIELD_STATUS = "http_response_status";
  private static final String MDC_FIELD_URI = "http_request_uri";

  private static final List<String> MDC_FIELDS =
      List.of(
          MDC_FIELD_RESOURCE_CLASS,
          MDC_FIELD_RESOURCE_METHOD,
          MDC_FIELD_STATUS,
          MDC_FIELD_HTTP_METHOD,
          MDC_FIELD_URI,
          MDC_FIELD_REQUEST_BODY,
          MDC_FIELD_RESPONSE_BODY,
          MDC_FIELD_DURATION);

  public static boolean isEnabled() {
    return log.isDebugEnabled();
  }

  @Context ResourceInfo resourceInfo;

  @Override
  public void filter(ContainerRequestContext requestContext) {
    requestContext.setProperty(REQUEST_TIME_PROPERTY, System.nanoTime());

    // put HTTP
    MDC.put(MDC_FIELD_HTTP_METHOD, requestContext.getMethod());
    MDC.put(MDC_FIELD_URI, requestContext.getUriInfo().getRequestUri().toString());

    MDC.put(MDC_FIELD_REQUEST_HEADERS, formatHeaders(requestContext));

    Optional.ofNullable(resourceInfo.getResourceClass())
        .map(Class::getSimpleName)
        .ifPresent(value -> MDC.put(MDC_FIELD_RESOURCE_CLASS, value));
    Optional.ofNullable(resourceInfo.getResourceMethod())
        .map(Method::getName)
        .ifPresent(value -> MDC.put(MDC_FIELD_RESOURCE_METHOD, value));

    // Logs directly from filter in case no request body is expected as aroundReadFrom will not be
    // called
    if (!(requestContext.hasEntity() && requestContext.getLength() != 0)) {
      logRequest();
    }
  }

  private String formatHeaders(ContainerRequestContext requestContext) {
    var sb = new StringBuilder();
    for (var entry : requestContext.getHeaders().entrySet()) {
      for (String v : entry.getValue()) {
        sb.append(entry.getKey()).append(": ").append(v).append("\n");
      }
    }
    return sb.toString();
  }

  @Override
  public Object aroundReadFrom(ReaderInterceptorContext context)
      throws IOException, WebApplicationException {

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    var teeInputStream =
        new TeeInputStream(context.getInputStream(), new BoundedOutputStream(outputStream, 10_000));
    context.setInputStream(teeInputStream);
    var entity = context.proceed();
    var body = outputStream.toString();
    MDC.put(MDC_FIELD_REQUEST_BODY, body);

    if (body != null && !body.isBlank()) {
      logRequest();
    }

    return entity;
  }

  /**
   * Logs the request received by the server. Note that the request method and URI must have been
   * stored in MDC before calling this method.
   */
  private void logRequest() {
    var method = MDC.get(MDC_FIELD_HTTP_METHOD);
    var uri = MDC.get(MDC_FIELD_URI);
    log.atDebug().log("{} {}", method, uri);
  }

  @Override
  public void filter(
      ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
    long requestStartTime =
        Optional.ofNullable(requestContext.getProperty(REQUEST_TIME_PROPERTY))
            .map(Object::toString)
            .map(Long::parseLong)
            .orElse(System.nanoTime());
    var duration = Duration.ofNanos(System.nanoTime() - requestStartTime);
    MDC.put(
        MDC_FIELD_DURATION, "%d.%03ds".formatted(duration.toSeconds(), duration.toMillisPart()));
    MDC.put(MDC_FIELD_STATUS, String.valueOf(responseContext.getStatus()));

    // Logs directly from filter in case no response body is present as aroundWriteTo will not be
    // called
    if (!responseContext.hasEntity()) {
      logResponse();
    }
  }

  @Override
  public void aroundWriteTo(WriterInterceptorContext context)
      throws IOException, WebApplicationException {
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      var teeOutputStream =
          new TeeOutputStream(
              context.getOutputStream(), new BoundedOutputStream(outputStream, 10_000)); // 10kb
      context.setOutputStream(teeOutputStream);
      context.proceed();

      MDC.put(MDC_FIELD_RESPONSE_BODY, outputStream.toString());

      logResponse();
    }
  }

  /**
   * Logs the response sent by the server. Note that the response status and duration must have been
   * stored in MDC before calling this method.
   */
  private void logResponse() {
    try {
      log.atDebug().log(
          "{} {} {} {}",
          MDC.get(MDC_FIELD_HTTP_METHOD),
          MDC.get(MDC_FIELD_URI),
          MDC.get(MDC_FIELD_STATUS),
          MDC.get(MDC_FIELD_DURATION));
    } finally {
      cleanupMdc();
    }
  }

  /** Removes all MDC fields we've previously set */
  private void cleanupMdc() {
    MDC_FIELDS.forEach(MDC::remove);
  }
}
