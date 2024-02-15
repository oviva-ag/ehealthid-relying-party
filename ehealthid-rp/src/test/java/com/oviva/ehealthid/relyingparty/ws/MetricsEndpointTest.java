package com.oviva.ehealthid.relyingparty.ws;

import static org.junit.jupiter.api.Assertions.*;

import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import jakarta.ws.rs.core.Response.Status;
import org.junit.jupiter.api.Test;

public class MetricsEndpointTest {

  @Test
  void get() {

    // given
    var prometheusMeterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    var sut = new MetricsEndpoint(prometheusMeterRegistry);

    // when
    var res = sut.get();

    // then
    assertEquals(Status.OK.getStatusCode(), res.getStatus());
  }
}
