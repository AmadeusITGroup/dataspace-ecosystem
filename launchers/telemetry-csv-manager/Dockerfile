ARG REGISTRY_URL="quay.io/centos"
ARG BASE_IMAGE="centos:latest"
FROM ${REGISTRY_URL}/${BASE_IMAGE}
# Set INSTALL_JAVA=false only if the base image already includes a JRE (e.g. eclipse-temurin).
ARG INSTALL_JAVA=true
RUN if [ "$INSTALL_JAVA" = "true" ]; then yum update -y && yum clean all; fi
RUN if [ "$INSTALL_JAVA" = "true" ]; then yum install java-21-openjdk -y && yum clean all; fi
# Optional JVM arguments, such as memory settings
ARG JVM_ARGS=""
ARG JAR
ARG OTEL_JAR

WORKDIR /app

# Create a group and user (non-root) with fixed UID/GID
RUN groupadd -g 1000 appuser && useradd -u 1000 -g appuser -s /bin/sh -m appuser

COPY ${JAR} telemetry-csv-manager.jar
COPY ${OTEL_JAR} opentelemetry-javaagent.jar

# Change ownership of /app to the non-root user
RUN chown -R appuser:appuser /app

HEALTHCHECK NONE

# default api
ENV WEB_HTTP_PORT="8080"
ENV WEB_HTTP_PATH="/api"

# ARG can not be used in ENTRYPOINT so storing value in an ENV variable
ENV ENV_JVM_ARGS=$JVM_ARGS

# Execute the container as non-root user
USER appuser

CMD ["java", \
    "-javaagent:/app/opentelemetry-javaagent.jar", \
     "-Djava.util.logging.config.file=/app/logging.properties", \
     "-Dotel.javaagent.configuration-file=/app/opentelemetry.properties", \
     "-Djava.security.egd=file:/dev/urandom", \
     "-jar", \
     "telemetry-csv-manager.jar"]