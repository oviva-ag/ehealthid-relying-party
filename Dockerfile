FROM registry.access.redhat.com/ubi9/ubi-minimal:9.3

LABEL org.opencontainers.image.source="https://github.com/oviva-ag/ehealthid-relying-party"

ARG JAVA_PACKAGE=java-21-openjdk-headless
ARG RUN_JAVA_VERSION=1.3.8

ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en'

# Install java and the run-java script
# Also set up permissions for user `1001`
RUN microdnf -y install ca-certificates ${JAVA_PACKAGE} \
    && microdnf -y update \
    && microdnf clean all \
    && mkdir /deployments \
    && chown 1001 /deployments \
    && chmod "g+rwX" /deployments \
    && chown 1001:root /deployments \
    && curl https://repo1.maven.org/maven2/io/fabric8/run-java-sh/${RUN_JAVA_VERSION}/run-java-sh-${RUN_JAVA_VERSION}-sh.sh -o /deployments/run-java.sh \
    && chown 1001 /deployments/run-java.sh \
    && chmod 540 /deployments/run-java.sh \
    && echo "securerandom.source=file:/dev/urandom" >> /etc/alternatives/jre/conf/security/java.security \
    && echo "securerandom.strongAlgorithms=NativePRNGNonBlocking:SUN,DRBG:SUN" >> /etc/alternatives/jre/conf/security/java.security

COPY --chown=1001 ehealthid-rp/target/ehealthid-rp-jar-with-dependencies.jar /deployments/

USER 1001

# The default port, configurable though.
EXPOSE 1234

ENTRYPOINT [ "/deployments/run-java.sh" ]