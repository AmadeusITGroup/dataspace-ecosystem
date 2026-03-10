# -buster is required to have apt available
FROM openjdk:17.0.2-slim-bullseye

# Optional JVM arguments, such as memory settings
ARG JVM_ARGS=""
ARG JAR_FILE=backend-service-provider.jar

# Install curl, then delete apt indexes to save image space
RUN apt update \
    && apt install -y curl \
    && rm -rf /var/cache/apt/archives /var/lib/apt/lists

WORKDIR /app

COPY ./build/shadow/${JAR_FILE} /app

# health status is determined by the availability of the /health endpoint
HEALTHCHECK --interval=5s --timeout=5s --retries=10 CMD curl --fail http://localhost:8080/api/check/health

ENV WEB_HTTP_PORT="8080"
ENV WEB_HTTP_PATH="/api"

# Use "exec" for graceful termination (SIGINT) to reach JVM.
# ARG can not be used in ENTRYPOINT so storing values in ENV variables
ENV JVM_ARGS=$JVM_ARGS
ENV JAR_FILE=$JAR_FILE
ENTRYPOINT [ "sh", "-c", \
    "exec java $JVM_ARGS -jar ${JAR_FILE}"]