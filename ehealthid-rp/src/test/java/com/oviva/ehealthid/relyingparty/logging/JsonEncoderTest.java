package com.oviva.ehealthid.relyingparty.logging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.KeyValuePair;

class JsonEncoderTest {

  private static final Instant TIMESTAMP = Instant.parse("2024-08-09T14:13:33Z");
  private static final String LOGGER_NAME = "com.example.MyLogger";
  private static final String THREAD_NAME = "main";
  private final JsonEncoder encoder = new JsonEncoder();

  @RegisterExtension
  static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

  @BeforeEach
  void beforeEach() {
    encoder.headerBytes();
  }

  @AfterEach
  void afterEach() {
    encoder.footerBytes();
  }

  @Test
  void encode_simple() {

    var e = mockEvent();
    when(e.getFormattedMessage()).thenReturn("Hello World!");

    var msg = encoder.encode(e);

    assertMatchesJson(
        """
          {"logger":"com.example.MyLogger","message":"Hello World!","severity":"INFO","thread_name":"main","time":"2024-08-09T14:13:33Z"}
          """,
        msg);
  }

  @Test
  void encode_fallback() {

    var e = mockEvent();
    doThrow(NullPointerException.class).when(e).getKeyValuePairs();

    var msg = encoder.encode(e);

    assertMatchesJson(
        """
              {"message":"error serializing log record: null","serviceContext":{"service":"","version":""},"severity":"ERROR","stack_trace":"java.lang.NullPointerException\\n","time":"2024-08-09T14:13:33Z"}
              """,
        msg);
  }

  @Test
  void encode_error() {

    var e = mockEvent();
    when(e.getLevel()).thenReturn(Level.ERROR);
    when(e.getFormattedMessage()).thenReturn("what a terrible failure");

    var msg = encoder.encode(e);

    assertMatchesJson(
        """
                  {"@type":"type.googleapis.com/google.devtools.clouderrorreporting.v1beta1.ReportedErrorEvent","logger":"com.example.MyLogger","message":"what a terrible failure","severity":"ERROR","thread_name":"main","time":"2024-08-09T14:13:33Z"}
                  """,
        msg);
  }

  @Test
  void encode_mdc() {

    var e = mockEvent();
    when(e.getLevel()).thenReturn(Level.DEBUG);
    when(e.getFormattedMessage()).thenReturn("oha, sup?");

    when(e.getMDCPropertyMap())
        .thenReturn(
            Map.of(
                "traceId", "k398cidkekk",
                "spanId", "499910"));

    var msg = encoder.encode(e);

    assertMatchesJson(
        """
                  {"logger":"com.example.MyLogger","mdc":{"spanId":"499910","traceId":"k398cidkekk"},"message":"oha, sup?","severity":"DEBUG","thread_name":"main","time":"2024-08-09T14:13:33Z"}
                  """,
        msg);
  }

  @Test
  void encode_kv() {

    var e = mockEvent();
    when(e.getLevel()).thenReturn(Level.DEBUG);
    when(e.getFormattedMessage()).thenReturn("oha, sup?");

    when(e.getKeyValuePairs())
        .thenReturn(
            List.of(
                new KeyValuePair("req", Map.of("url", "https://example.com", "method", "GET")),
                new KeyValuePair("a", Map.of("b", Map.of("c", "d"))),
                new KeyValuePair("status", 500)));

    var msg = encoder.encode(e);

    assertMatchesJson(
        """
                      {"a":{"b":{"c":"d"}},"logger":"com.example.MyLogger","message":"oha, sup?","req":{"method":"GET","url":"https://example.com"},"severity":"DEBUG","status":"500","thread_name":"main","time":"2024-08-09T14:13:33Z"}
                      """,
        msg);
  }

  @Test
  void encode_otel() {

    var tracer = otelTesting.getOpenTelemetry().getTracer("test");

    var span = tracer.spanBuilder("log").startSpan();
    byte[] msg = null;
    try (var scope = span.makeCurrent()) {
      var e = mockEvent();
      when(e.getLevel()).thenReturn(Level.DEBUG);
      when(e.getFormattedMessage()).thenReturn("oha, sup?");

      msg = encoder.encode(e);
    }

    var traceId = span.getSpanContext().getTraceId();
    var spanId = span.getSpanContext().getSpanId();

    assertMatchesJson(
        """
                      {"logger":"com.example.MyLogger","logging.googleapis.com/spanId":"%s","logging.googleapis.com/trace":"%s","logging.googleapis.com/trace_sampled":true,"message":"oha, sup?","severity":"DEBUG","thread_name":"main","time":"2024-08-09T14:13:33Z"}
                      """
            .formatted(spanId, traceId),
        msg);
  }

  private ILoggingEvent mockEvent() {

    var e = mock(ILoggingEvent.class);
    when(e.getInstant()).thenReturn(TIMESTAMP);
    when(e.getLevel()).thenReturn(Level.INFO);
    when(e.getLoggerName()).thenReturn(LOGGER_NAME);
    when(e.getThreadName()).thenReturn(THREAD_NAME);
    return e;
  }

  void assertMatchesJson(String expected, byte[] actual) {
    var mapper = JsonMapper.builder().nodeFactory(new SortingNodeFactory()).build();

    try {
      var expectedTree = mapper.readTree(expected);
      var actualTree = mapper.readTree(actual);

      assertEquals(mapper.writeValueAsString(expectedTree), mapper.writeValueAsString(actualTree));
    } catch (IOException e) {
      fail(e);
    }
  }

  static class SortingNodeFactory extends JsonNodeFactory {
    @Override
    public ObjectNode objectNode() {
      return new ObjectNode(this, new TreeMap<String, JsonNode>());
    }
  }
}
