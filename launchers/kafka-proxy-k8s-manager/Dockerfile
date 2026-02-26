ARG REGISTRY_URL="quay.io/centos"
ARG BASE_IMAGE="centos:latest"
FROM ${REGISTRY_URL}/${BASE_IMAGE}
# Set INSTALL_JAVA=false only if the base image already includes a JRE (e.g. eclipse-temurin).
ARG INSTALL_JAVA=true
RUN if [ "$INSTALL_JAVA" = "true" ]; then yum update -y && yum clean all; fi
RUN if [ "$INSTALL_JAVA" = "true" ]; then yum install java-21-openjdk -y && yum clean all; fi

WORKDIR /app

# Create non-root user
RUN groupadd -g 1000 appuser && useradd -u 1000 -g appuser -s /bin/sh -m appuser

COPY ./build/libs/kafka-proxy-k8s-manager.jar /app/

RUN mkdir -p /shared /tmp && chown -R appuser:appuser /app /shared /tmp

EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

USER appuser

CMD ["sh", "-c", "java $JAVA_OPTS -jar kafka-proxy-k8s-manager.jar"]