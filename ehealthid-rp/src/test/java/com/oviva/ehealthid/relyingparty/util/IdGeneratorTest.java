package com.oviva.ehealthid.relyingparty.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class IdGeneratorTest {

  @Test
  void generate_unique() {

    var previous = new HashSet<>();

    for (int i = 0; i < 100; i++) {

      var next = IdGenerator.generateID();
      if (previous.contains(next)) {
        fail();
      }
      previous.add(next);
    }
  }
}
