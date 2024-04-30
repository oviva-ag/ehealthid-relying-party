package com.oviva.ehealthid.relyingparty.ws;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider;
import com.oviva.ehealthid.util.JoseModule;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import jakarta.ws.rs.core.Application;
import java.util.Set;

public class ManagementApp extends Application {

  private final PrometheusMeterRegistry prometheusMeterRegistry;

  public ManagementApp(PrometheusMeterRegistry prometheusMeterRegistry) {
    this.prometheusMeterRegistry = prometheusMeterRegistry;
  }

  @Override
  public Set<Object> getSingletons() {
    return Set.of(
        new HealthEndpoint(),
        new MetricsEndpoint(prometheusMeterRegistry),
        new JacksonJsonProvider(configureObjectMapper()));
  }

  @Override
  public Set<Class<?>> getClasses() {
    return Set.of(ThrowableExceptionMapper.class);
  }

  private ObjectMapper configureObjectMapper() {
    var om = new ObjectMapper();
    om.registerModule(new JoseModule());
    om.setSerializationInclusion(Include.NON_NULL);
    return om;
  }
}
