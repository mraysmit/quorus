# syntax=docker/dockerfile:1.4
# Multi-stage build for Quorus Controller
# Requires Docker BuildKit (DOCKER_BUILDKIT=1)
#
# BUILD COMMAND (uses additional build context for local Maven artifacts):
#   docker build --build-context m2cache=$HOME/.m2/repository \
#                -f quorus-controller/Dockerfile -t quorus-controller .
#
# This eliminates the need for an m2-repo directory in the build context.
# The raftlog-core dependency (not on Maven Central) is pulled from your
# local Maven cache via BuildKit's named context feature.

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

# Create empty stub poms for modules not needed for controller build
RUN mkdir -p quorus-integration-examples && echo '<?xml version="1.0" encoding="UTF-8"?><project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"><modelVersion>4.0.0</modelVersion><parent><groupId>dev.mars</groupId><artifactId>quorus</artifactId><version>1.0-SNAPSHOT</version></parent><artifactId>quorus-integration-examples</artifactId><packaging>pom</packaging></project>' > quorus-integration-examples/pom.xml

RUN mkdir -p quorus-agent && echo '<?xml version="1.0" encoding="UTF-8"?><project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"><modelVersion>4.0.0</modelVersion><parent><groupId>dev.mars</groupId><artifactId>quorus</artifactId><version>1.0-SNAPSHOT</version></parent><artifactId>quorus-agent</artifactId><packaging>pom</packaging></project>' > quorus-agent/pom.xml

# Copy local Maven artifacts from named build context (raftlog-core not on Maven Central)
# BuildKit pulls these from host's ~/.m2/repository/dev/mars via --build-context m2cache=...
# Context is pointed directly at dev/mars to avoid transferring the entire Maven cache
COPY --from=m2cache / /root/.m2/repository/dev/mars

# Download dependencies (cached via Docker BuildKit cache mount)
RUN --mount=type=cache,target=/root/.m2/repository/org \
    --mount=type=cache,target=/root/.m2/repository/io \
    --mount=type=cache,target=/root/.m2/repository/com \
    --mount=type=cache,target=/root/.m2/repository/net \
    mvn dependency:go-offline -pl quorus-controller -am -Dsilent=true || true

# Copy source code (changes here don't invalidate dependency layer)
COPY quorus-core/src quorus-core/src
COPY quorus-workflow/src quorus-workflow/src
COPY quorus-tenant/src quorus-tenant/src
COPY quorus-api/src quorus-api/src
COPY quorus-controller/src quorus-controller/src

# Build the application
# -U flag removed: no longer needed since raftlog is properly available
RUN --mount=type=cache,target=/root/.m2/repository/org \
    --mount=type=cache,target=/root/.m2/repository/io \
    --mount=type=cache,target=/root/.m2/repository/com \
    --mount=type=cache,target=/root/.m2/repository/net \
    mvn package -pl quorus-controller -am -DskipTests

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
