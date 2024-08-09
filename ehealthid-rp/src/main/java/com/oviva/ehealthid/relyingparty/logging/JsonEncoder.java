package com.oviva.ehealthid.relyingparty.logging;

import ch.qos.logback.classic.pattern.ExtendedThrowableProxyConverter;
import ch.qos.logback.classic.pattern.ThrowableHandlingConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.EncoderBase;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.JsonStringEncoder;
import io.opentelemetry.api.trace.Span;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.slf4j.event.KeyValuePair;

/** GCP flavoured JSON logging */
public class JsonEncoder extends EncoderBase<ILoggingEvent> {

  // https://cloud.google.com/error-reporting/docs/formatting-error-messages
  private static final String REPORTED_ERROR_TYPE =
      "type.googleapis.com/google.devtools.clouderrorreporting.v1beta1.ReportedErrorEvent";

  private static final String SOURCE_LOCATION_FIELD = "logging.googleapis.com/sourceLocation";

  private final JsonFactory jsonFactory = new JsonFactory();
  private final DateTimeFormatter timeFormatter = DateTimeFormatter.ISO_INSTANT;
  private final ThrowableHandlingConverter throwableConverter =
      new ExtendedThrowableProxyConverter();

  private final String serviceName;
  private final String serviceVersion;

  public JsonEncoder() {
    var pkg = JsonEncoder.class.getPackage();
    serviceName = pkg.getImplementationTitle();
    serviceVersion = pkg.getImplementationVersion();
  }

  @Override
  public byte[] encode(ILoggingEvent event) {
    var baos = new ByteArrayOutputStream();
    try (var generator = jsonFactory.createGenerator(baos)) {
      generator.writeStartObject();

      // https://cloud.google.com/logging/docs/structured-logging#structured_logging_special_fields
      // https://github.com/googleapis/java-logging-logback/blob/main/src/main/java/com/google/cloud/logging/logback/LoggingAppender.java

      writeTimestamp(generator, event);
      writeSeverity(generator, event);
      writeLogger(generator, event);
      writeMessage(generator, event);
      writeThread(generator, event);

      writeServiceContext(generator);
      writeTraceContext(generator);

      var mdc = event.getMDCPropertyMap();
      writeMdc(generator, mdc);

      writeKeyValue(generator, event);

      if ("ERROR".equals(event.getLevel().toString())) {
        writeError(generator, event, mdc);
      }
      writeStackTrace(generator, event);

      generator.writeEndObject();
      generator.writeRaw('\n');
      generator.flush();
    } catch (NullPointerException | IOException e) {
      return logFallbackError(event, e);
    }
    return baos.toByteArray();
  }

  private byte[] logFallbackError(ILoggingEvent event, Throwable t) {
    // skipping JSON encoding and falling back to a very basic message
    var escapedMessage =
        escapeJsonOrDefault(
            t.getMessage(), "error serializing log record: " + event.getFormattedMessage());

    var sn = escapeJsonOrDefault(this.serviceName, "");
    var sv = escapeJsonOrDefault(this.serviceVersion, "");

    var stackTrace = escapeJson(stringifyStackTrace(t));

    return """
       {"time":"%s","severity":"ERROR","message":"%s","stack_trace":"%s","serviceContext":{"service":"%s","version":"%s"}}
      """
        .formatted(timeFormatter.format(event.getInstant()), escapedMessage, stackTrace, sn, sv)
        .getBytes(StandardCharsets.UTF_8);
  }

  private void writeServiceContext(JsonGenerator generator) throws IOException {

    var name = this.serviceName;
    var version = this.serviceVersion;

    if (name == null && version == null) {
      return;
    }

    generator.writeObjectFieldStart("serviceContext");
    if (name != null && !name.isEmpty()) {
      generator.writeStringField("service", this.serviceName);
    }
    if (version != null && !version.isEmpty()) {
      generator.writeStringField("version", this.serviceVersion);
    }

    generator.writeEndObject();
  }

  private void writeLogger(JsonGenerator generator, ILoggingEvent logRecord) throws IOException {
    generator.writeStringField("logger", logRecord.getLoggerName());
  }

  private void writeMessage(JsonGenerator generator, ILoggingEvent logRecord) throws IOException {
    generator.writeStringField("message", logRecord.getFormattedMessage());
  }

  private void writeSeverity(JsonGenerator generator, ILoggingEvent logRecord) throws IOException {
    // should we map that to the GCP levels?
    generator.writeStringField("severity", logRecord.getLevel().toString());
  }

  private void writeTimestamp(JsonGenerator generator, ILoggingEvent logRecord) throws IOException {
    generator.writeStringField("time", timeFormatter.format(logRecord.getInstant()));
  }

  private void writeThread(JsonGenerator generator, ILoggingEvent logRecord) throws IOException {
    generator.writeStringField("thread_name", logRecord.getThreadName());
  }

  private void writeError(JsonGenerator generator, ILoggingEvent logRecord, Map<String, String> mdc)
      throws IOException {

    // https://cloud.google.com/error-reporting/docs/formatting-error-messages

    generator.writeStringField("@type", REPORTED_ERROR_TYPE);
    writeSourceLocation(generator, logRecord);
  }

  private static void writeSourceLocation(JsonGenerator generator, ILoggingEvent logRecord)
      throws IOException {
    var stack = logRecord.getCallerData();
    if (stack == null || stack.length == 0) {
      return;
    }

    var topFrame = stack[0];

    generator.writeObjectFieldStart(SOURCE_LOCATION_FIELD);
    generator.writeStringField("file", topFrame.getFileName());
    generator.writeStringField("line", Integer.toString(topFrame.getLineNumber()));

    var className = topFrame.getClassName();
    var methodName = topFrame.getMethodName();

    generator.writeStringField("function", "%s.%s".formatted(className, methodName));
    generator.writeEndObject();
  }

  private void writeMdc(JsonGenerator generator, Map<String, String> mdc) throws IOException {
    if (mdc.isEmpty()) {
      return;
    }

    generator.writeObjectFieldStart("mdc");

    for (Map.Entry<String, String> entry : mdc.entrySet()) {
      if (entry.getValue() == null) {
        continue;
      }
      generator.writeStringField(entry.getKey(), entry.getValue());
    }

    generator.writeEndObject();
  }

  private void writeKeyValue(JsonGenerator generator, ILoggingEvent event) throws IOException {
    var kvPairs = event.getKeyValuePairs();
    if (kvPairs == null || kvPairs.isEmpty()) {
      return;
    }

    for (KeyValuePair pair : kvPairs) {
      if (pair.key == null || pair.value == null) {
        continue;
      }
      if (pair.value instanceof Map m) {

        generator.writeObjectFieldStart(pair.key);
        writeValue(generator, m);
        generator.writeEndObject();
      } else {
        generator.writeStringField(pair.key, pair.value.toString());
      }
    }
  }

  private void writeValue(JsonGenerator generator, Map<String, Object> m) throws IOException {
    for (Map.Entry<String, Object> e : m.entrySet()) {
      if (e.getKey() == null || e.getValue() == null) {
        continue;
      }
      if (e.getValue() instanceof Map m2) {
        generator.writeObjectFieldStart(e.getKey());
        writeValue(generator, m2);
        generator.writeEndObject();
      } else {
        generator.writeStringField(e.getKey(), e.getValue().toString());
      }
    }
  }

  private static void writeTraceContext(JsonGenerator generator) throws IOException {
    var span = Span.current();
    if (span == null) {
      return;
    }
    var spanContext = span.getSpanContext();
    if (spanContext == null || !spanContext.isValid()) {
      return;
    }

    generator.writeStringField("logging.googleapis.com/spanId", spanContext.getSpanId());
    generator.writeStringField("logging.googleapis.com/trace", spanContext.getTraceId());
    generator.writeBooleanField("logging.googleapis.com/trace_sampled", spanContext.isSampled());
  }

  private void writeStackTrace(JsonGenerator generator, ILoggingEvent event) throws IOException {
    var t = event.getThrowableProxy();
    if (t == null) {
      return;
    }
    var stackTrace = throwableConverter.convert(event);
    generator.writeStringField("stack_trace", stackTrace);
  }

  @Override
  public byte[] headerBytes() {
    throwableConverter.start();
    return null;
  }

  @Override
  public byte[] footerBytes() {
    throwableConverter.stop();
    return null;
  }

  private static String escapeJsonOrDefault(String s, String defaultValue) {

    if (s == null) {
      return defaultValue;
    }
    s = s.strip();
    if (s.isEmpty()) {
      return defaultValue;
    }

    return escapeJson(s);
  }

  private static String escapeJson(String s) {
    return new String(JsonStringEncoder.getInstance().quoteAsString(s));
  }

  private static String stringifyStackTrace(Throwable t) {
    var baos = new ByteArrayOutputStream();
    var pw = new PrintWriter(baos);
    t.printStackTrace(pw);
    pw.flush();
    return baos.toString(StandardCharsets.UTF_8);
  }
}
