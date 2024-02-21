package com.oviva.ehealthid.relyingparty.ws;

import static org.junit.jupiter.api.Assertions.*;

import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

class MetricsEndpointTest {

  @Test
  void get() {

    // given
    var prometheusMeterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

    var counter = prometheusMeterRegistry.counter("test.counter");

    counter.increment(2.0);

    var sut = new MetricsEndpoint(prometheusMeterRegistry);

    // when
    var res = sut.get();

    // then
    assertTrue(res.contains("test_counter_total 2.0"));
  }
}
