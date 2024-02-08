package com.oviva.ehealthid.relyingparty.ws;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.ws.rs.core.Response.Status;
import org.junit.jupiter.api.Test;

class HealthEndpointTest {

  @Test
  void get() {
    var sut = new HealthEndpoint();

    // when
    var res = sut.get();

    // then
    assertEquals(Status.OK.getStatusCode(), res.getStatus());
  }
}
