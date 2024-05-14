package com.oviva.ehealthid.relyingparty.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.oviva.ehealthid.fedclient.api.HttpClient;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class LoggingHttpClientTest {

  private Logger logger = (Logger) LoggerFactory.getLogger(LoggingHttpClient.class);
  private ListAppender<ILoggingEvent> logs;

  @BeforeEach
  void setupLogger() {
    var fooLogger = (Logger) LoggerFactory.getLogger(LoggingHttpClient.class);

    logs = new ListAppender<>();
    logs.start();

    fooLogger.addAppender(logs);
  }

  @Test
  void infoLevel() {

    var res = new HttpClient.Response(200, List.of(), null);

    var delegate = mock(HttpClient.class);
    when(delegate.call(any())).thenReturn(res);

    var sut = new LoggingHttpClient(delegate);

    logger.setLevel(Level.INFO);

    // when
    sut.call(
        new HttpClient.Request(URI.create("http://localhost:1234/test"), "GET", List.of(), null));

    // then
    assertTrue(logs.list.isEmpty());
  }

  @Test
  void logCall() {

    var body = "Hello World!";
    var res =
        new HttpClient.Response(
            200,
            List.of(new HttpClient.Header("content-type", "text/plain")),
            body.getBytes(StandardCharsets.UTF_8));

    var delegate = mock(HttpClient.class);
    when(delegate.call(any())).thenReturn(res);

    var sut = new LoggingHttpClient(delegate);

    logger.setLevel(Level.DEBUG);

    // when
    sut.call(
        new HttpClient.Request(URI.create("http://localhost:1234/test"), "GET", List.of(), null));

    // then
    assertEquals(2, logs.list.size());

    var requestLog = logs.list.get(0);
    var responseLog = logs.list.get(1);

    assertRequestLog(requestLog);
    assertResponseLog(responseLog, res);
  }

  private void assertRequestLog(ILoggingEvent log) {
    assertEquals(Level.DEBUG, log.getLevel());
    assertEquals("request: GET http://localhost:1234/test", log.getMessage());
  }

  private void assertResponseLog(ILoggingEvent log, HttpClient.Response expected) {

    assertEquals(Level.DEBUG, log.getLevel());
    assertEquals("response: GET http://localhost:1234/test 200", log.getMessage());
    var gotBody =
        log.getKeyValuePairs().stream()
            .filter(pair -> Objects.equals(pair.key, "body"))
            .map(p -> (String) p.value)
            .findFirst()
            .orElse(null);

    assertEquals(new String(expected.body(), StandardCharsets.UTF_8), gotBody);
  }
}
