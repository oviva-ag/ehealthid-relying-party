FROM gcr.io/distroless/java25-debian13:nonroot@sha256:1c4ced761d17961c900cba60c33c8c3c46edbdadaa9d5ef9806eb5a6824fd406

LABEL org.opencontainers.image.source="https://github.com/oviva-ag/ehealthid-relying-party"

# Configure OpenTelemetry
ENV OTEL_JAVAAGENT_DEBUG=false
ENV OTEL_JAVAAGENT_ENABLED=false
ENV OTEL_METRICS_EXPORTER=none
ENV OTEL_LOGS_EXPORTER=none
ENV OTEL_TRACES_EXPORTER=otlp
ENV OTEL_EXPORTER_OTLP_TRACES_PROTOCOL=grpc

## Allowlist instrumented components for faster startup
ENV OTEL_INSTRUMENTATION_COMMON_DEFAULT_ENABLED=false
ENV OTEL_INSTRUMENTATION_JAVA_HTTP_CLIENT_ENABLED=true
ENV OTEL_INSTRUMENTATION_JAXRS_ENABLED=true
ENV OTEL_INSTRUMENTATION_UNDERTOW_ENABLED=true

# The agent is fetched by the Maven build, see ehealthid-rp/pom.xml
COPY ehealthid-rp/target/opentelemetry-javaagent.jar /opentelemetry-javaagent.jar
COPY ehealthid-rp/src/main/docker/java-security.properties /deployments/java-security.properties
COPY ehealthid-rp/target/ehealthid-rp-jar-with-dependencies.jar /deployments/

USER nonroot

# The default port, configurable though.
EXPOSE 1234

# Add JVM options with JDK_JAVA_OPTIONS, e.g. -XshowSettings:vm to display the heap size.
ENTRYPOINT [ "java", \
  "-XX:MaxRAMPercentage=50", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Djava.security.properties=/deployments/java-security.properties", \
  "-javaagent:/opentelemetry-javaagent.jar", \
  "-jar", "/deployments/ehealthid-rp-jar-with-dependencies.jar" ]
