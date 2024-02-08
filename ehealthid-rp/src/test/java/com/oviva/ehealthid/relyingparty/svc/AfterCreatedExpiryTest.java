package com.oviva.ehealthid.relyingparty.svc;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AfterCreatedExpiryTest {

  @Test
  void afterCreate() {

    var ttl = 12345L;
    var sut = new AfterCreatedExpiry(ttl);

    // when
    var expiry = sut.expireAfterCreate(null, null, 123L);

    // then
    assertEquals(ttl, expiry);
  }

  @Test
  void afterUpdate() {

    var ttl = 12345L;
    var sut = new AfterCreatedExpiry(ttl);

    var expiry1 = sut.expireAfterCreate(null, null, 3893L);

    // when
    var elapsed = 1000L;
    var expiry2 = sut.expireAfterUpdate(null, null, 39391L, elapsed);

    // then
    assertEquals(expiry1, expiry2 + elapsed);
  }

  @Test
  void afterRead() {

    var ttl = 12345L;
    var sut = new AfterCreatedExpiry(ttl);

    var expiry1 = sut.expireAfterCreate(null, null, 3893L);

    // when
    var elapsed = 1290L;
    var expiry2 = sut.expireAfterRead(null, null, 39391L, elapsed);

    // then
    assertEquals(expiry1, expiry2 + elapsed);
  }
}
