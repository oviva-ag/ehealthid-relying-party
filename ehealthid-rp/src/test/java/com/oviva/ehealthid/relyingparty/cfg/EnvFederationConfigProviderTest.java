package com.oviva.ehealthid.relyingparty.cfg;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class EnvFederationConfigProviderTest {

  private static final String PREFIX = "EHEALTHID_RP";

  static Stream<TC> mangleTestCases() {
    return Stream.of(
        new TC("my.config", PREFIX + "_MY_CONFIG"), new TC("a.no..ther", PREFIX + "_A_NO__THER"));
  }

  @ParameterizedTest
  @MethodSource("mangleTestCases")
  void getMangleName(TC t) {

    var getenv = (UnaryOperator<String>) mock(UnaryOperator.class);

    var sut = new EnvConfigProvider(PREFIX, getenv);

    // when
    sut.get(t.key());

    // then
    verify(getenv).apply(t.expected());
  }

  record TC(String key, String expected) {}
}
