# Multi-stage build for Quorus API
FROM maven:3.9.9-eclipse-temurin-21 AS builder

# Set working directory
WORKDIR /app

# Copy parent pom and module poms first for better layer caching
COPY pom.xml .
COPY quorus-core/pom.xml quorus-core/
COPY quorus-workflow/pom.xml quorus-workflow/
COPY quorus-tenant/pom.xml quorus-tenant/
COPY quorus-api/pom.xml quorus-api/
COPY quorus-controller/pom.xml quorus-controller/

# Create empty stub poms for modules not needed for API build
RUN mkdir -p quorus-integration-examples && echo '<?xml version="1.0" encoding="UTF-8"?><project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"><modelVersion>4.0.0</modelVersion><parent><groupId>dev.mars</groupId><artifactId>quorus</artifactId><version>1.0-SNAPSHOT</version></parent><artifactId>quorus-integration-examples</artifactId><packaging>pom</packaging></project>' > quorus-integration-examples/pom.xml

RUN mkdir -p quorus-agent && echo '<?xml version="1.0" encoding="UTF-8"?><project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"><modelVersion>4.0.0</modelVersion><parent><groupId>dev.mars</groupId><artifactId>quorus</artifactId><version>1.0-SNAPSHOT</version></parent><artifactId>quorus-agent</artifactId><packaging>pom</packaging></project>' > quorus-agent/pom.xml

# Download dependencies
RUN mvn dependency:go-offline -pl quorus-api -am

# Copy source code
COPY quorus-core/src quorus-core/src
COPY quorus-workflow/src quorus-workflow/src
COPY quorus-tenant/src quorus-tenant/src
COPY quorus-api/src quorus-api/src
COPY quorus-controller/src quorus-controller/src

# Build the application
RUN mvn clean package -pl quorus-api -am -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

# Install curl for health checks
RUN apk add --no-cache curl

# Create app user
RUN addgroup -g 1001 quorus && \
    adduser -D -s /bin/sh -u 1001 -G quorus quorus

# Set working directory
WORKDIR /app

# Copy the built JAR
COPY --from=builder /app/quorus-api/target/quarkus-app/ ./

# Create directories for logs
RUN mkdir -p /app/logs && \
    chown -R quorus:quorus /app

# Switch to non-root user
USER quorus

# Expose the HTTP port
EXPOSE 8080

# Environment variables
ENV JAVA_OPTS="-Xmx512m -Xms256m" \
    QUARKUS_HTTP_HOST=0.0.0.0 \
    QUARKUS_HTTP_PORT=8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${QUARKUS_HTTP_PORT}/health || exit 1

# Entry point script
COPY --chown=quorus:quorus quorus-api/docker-entrypoint.sh /app/
RUN chmod +x /app/docker-entrypoint.sh

ENTRYPOINT ["/app/docker-entrypoint.sh"]
CMD ["java", "-jar", "quarkus-run.jar"]
