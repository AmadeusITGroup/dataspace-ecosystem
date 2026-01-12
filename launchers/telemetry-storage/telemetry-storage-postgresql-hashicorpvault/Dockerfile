ARG REGISTRY_URL="docker.io"
FROM ${REGISTRY_URL}/eclipse-temurin:21.0.9_10-jre-alpine-3.23

# Optional JVM arguments, such as memory settings
ARG JVM_ARGS=""
ARG JAR
ARG OTEL_JAR

WORKDIR /app

COPY ${JAR} telemetrystorageservice.jar
COPY ${OTEL_JAR} opentelemetry-javaagent.jar

HEALTHCHECK NONE

# default api
ENV WEB_HTTP_PORT="8080"
ENV WEB_HTTP_PATH="/api"

# ARG can not be used in ENTRYPOINT so storing value in an ENV variable
ENV ENV_JVM_ARGS=$JVM_ARGS

CMD ["java", \
    "-javaagent:/app/opentelemetry-javaagent.jar", \
     "-Djava.util.logging.config.file=/app/logging.properties", \
     "-Dotel.javaagent.configuration-file=/app/opentelemetry.properties", \
     "-Djava.security.egd=file:/dev/urandom", \
     "-jar", \
     "telemetrystorageservice.jar"]