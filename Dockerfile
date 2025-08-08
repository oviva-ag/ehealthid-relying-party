FROM registry.access.redhat.com/ubi9/ubi-minimal:9.6

LABEL org.opencontainers.image.source="https://github.com/oviva-ag/ehealthid-relying-party"

ARG JAVA_PACKAGE=java-21-openjdk-headless
ARG RUN_JAVA_VERSION=1.3.8
ARG OTEL_AGENT_VERSION=v1.32.1

ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en'

# Install java and the run-java script
# Also set up permissions for user `1001`
RUN <<EOF
microdnf -y update
microdnf -y install ca-certificates ${JAVA_PACKAGE}
microdnf clean all
mkdir /deployments
chown 1001 /deployments
chmod "g+rwX" /deployments
chown 1001:root /deployments
curl https://repo1.maven.org/maven2/io/fabric8/run-java-sh/${RUN_JAVA_VERSION}/run-java-sh-${RUN_JAVA_VERSION}-sh.sh -o /deployments/run-java.sh
chown 1001 /deployments/run-java.sh
chmod 540 /deployments/run-java.sh
curl "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar" -o opentelemetry-javaagent.jar -L
echo "securerandom.source=file:/dev/urandom" >> /etc/alternatives/jre/conf/security/java.security
echo "securerandom.strongAlgorithms=NativePRNGNonBlocking:SUN,DRBG:SUN" >> /etc/alternatives/jre/conf/security/java.security
EOF

# Configure the JAVA_OPTIONS, you can add -XshowSettings:vm to also display the heap size.
ENV JAVA_OPTIONS="-javaagent:/opentelemetry-javaagent.jar"

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

COPY --chown=1001 ehealthid-rp/target/ehealthid-rp-jar-with-dependencies.jar /deployments/

USER 1001

# The default port, configurable though.
EXPOSE 1234

ENTRYPOINT [ "/deployments/run-java.sh" ]