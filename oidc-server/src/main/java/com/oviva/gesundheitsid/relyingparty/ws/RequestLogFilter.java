package com.oviva.gesundheitsid.relyingparty.ws;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestLogFilter implements ContainerResponseFilter {

  private final Logger logger = LoggerFactory.getLogger("http");

  @Override
  public void filter(
      ContainerRequestContext requestContext, ContainerResponseContext responseContext)
      throws IOException {

    logger
        .atInfo()
        .addKeyValue(
            "httpRequest",
            new HttpRequest(
                requestContext.getMethod(),
                requestContext.getUriInfo().getRequestUri().toString(),
                responseContext.getStatus(),
                requestContext.getLength(),
                responseContext.getLength(),
                requestContext.getHeaderString("user-agent"),
                null))
        .log(
            "{} {} {}",
            requestContext.getMethod(),
            requestContext.getUriInfo().getPath(),
            responseContext.getStatus());
  }

  private record HttpRequest(
      String requestMethod,
      String requestUrl,
      int status,
      int requestSize,
      int responseSize,
      String userAgent,
      String remoteIp) {}
}
