package com.oviva.ehealthid.relyingparty.ws;

import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/metrics")
public class MetricsEndpoint {

  private final PrometheusMeterRegistry registry;

  public MetricsEndpoint(PrometheusMeterRegistry registry) {
    this.registry = registry;

    new ClassLoaderMetrics().bindTo(this.registry);
    new JvmMemoryMetrics().bindTo(this.registry);
    new JvmGcMetrics().bindTo(this.registry);
    new ProcessorMetrics().bindTo(this.registry);
    new JvmThreadMetrics().bindTo(this.registry);
  }

  @GET
  @Produces(MediaType.TEXT_PLAIN)
  public Response get() {
    return Response.ok(this.registry.scrape(), MediaType.TEXT_PLAIN_TYPE).build();
  }
}
