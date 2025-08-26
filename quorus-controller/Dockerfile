# Multi-stage build for Quorus Controller
FROM maven:3.9.5-eclipse-temurin-21 AS builder

# Set working directory
WORKDIR /app

# Copy parent pom and module poms first for better layer caching
COPY pom.xml .
COPY quorus-core/pom.xml quorus-core/
COPY quorus-workflow/pom.xml quorus-workflow/
COPY quorus-tenant/pom.xml quorus-tenant/
COPY quorus-api/pom.xml quorus-api/
COPY quorus-controller/pom.xml quorus-controller/

# Create empty integration-examples directory to satisfy parent pom
RUN mkdir -p quorus-integration-examples && echo '<?xml version="1.0" encoding="UTF-8"?><project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"><modelVersion>4.0.0</modelVersion><parent><groupId>dev.mars</groupId><artifactId>quorus</artifactId><version>1.0-SNAPSHOT</version></parent><artifactId>quorus-integration-examples</artifactId><packaging>pom</packaging></project>' > quorus-integration-examples/pom.xml

# Download dependencies
RUN mvn dependency:go-offline -pl quorus-controller -am

# Copy source code
COPY quorus-core/src quorus-core/src
COPY quorus-workflow/src quorus-workflow/src
COPY quorus-tenant/src quorus-tenant/src
COPY quorus-api/src quorus-api/src
COPY quorus-controller/src quorus-controller/src

# Build the application
RUN mvn clean package -pl quorus-controller -am -DskipTests

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
COPY --from=builder /app/quorus-controller/target/quorus-controller-*.jar app.jar

# Create directories for logs and data
RUN mkdir -p /app/logs /app/data && \
    chown -R quorus:quorus /app

# Switch to non-root user
USER quorus

# Expose the Raft communication port
EXPOSE 8080

# Environment variables for Quorus Controller
ENV JAVA_OPTS="-Xmx512m -Xms256m" \
    NODE_ID="" \
    CLUSTER_NODES="" \
    RAFT_PORT=8080 \
    RAFT_HOST=0.0.0.0 \
    HTTP_PORT=8080 \
    ELECTION_TIMEOUT_MS=5000 \
    HEARTBEAT_INTERVAL_MS=1000

# Health check using controller's health endpoint
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${HTTP_PORT}/health || exit 1

# Run the Quorus Controller application
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
