package com.oviva.ehealthid.relyingparty.ws;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/health")
public class HealthEndpoint {

  private static final String STATUS_UP = "{\"status\":\"UP\"}";

  @GET
  public Response get() {
    // For now if this endpoint is reachable then the service is up. There is no hard dependency
    // that could be down.
    return Response.ok(STATUS_UP).type(MediaType.APPLICATION_JSON_TYPE).build();
  }
}
