package com.oviva.ehealthid.fedclient.api;

import static org.junit.jupiter.api.Assertions.*;

import com.oviva.ehealthid.test.ECKeyGenerator;
import org.junit.jupiter.api.Test;

class EntityStatementTest {

  @Test
  void sign_roundTrip() {

    var key = ECKeyGenerator.example();

    var sub = "hello world!";

    // when
    var jws = EntityStatement.create().sub(sub).build().sign(key);

    // then
    var got = EntityStatementJWS.parse(jws.serialize());
    assertEquals(sub, got.body().sub());
  }
}
