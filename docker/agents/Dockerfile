# Multi-stage build for Quorus Agent
FROM maven:3.9.6-eclipse-temurin-21 AS builder

# Set working directory
WORKDIR /app

# Copy parent POM and module POMs first for better layer caching
COPY pom.xml .
COPY quorus-core/pom.xml quorus-core/
COPY quorus-workflow/pom.xml quorus-workflow/

# Download dependencies
RUN mvn dependency:go-offline -pl quorus-core,quorus-workflow

# Copy source code
COPY quorus-core/src quorus-core/src
COPY quorus-workflow/src quorus-workflow/src

# Build the core modules
RUN mvn clean package -pl quorus-core,quorus-workflow -DskipTests

# Copy agent source
COPY docker/agents/src docker/agents/src
COPY docker/agents/pom.xml docker/agents/

# Build the agent
RUN mvn clean package -f docker/agents/pom.xml -DskipTests

# ============================================================================
# Runtime stage
FROM eclipse-temurin:21-jre-alpine

# Install required packages
RUN apk add --no-cache \
    curl \
    bash \
    tzdata \
    && rm -rf /var/cache/apk/*

# Create quorus user
RUN addgroup -g 1001 quorus && \
    adduser -D -u 1001 -G quorus quorus

# Set working directory
WORKDIR /app

# Copy the built JAR and dependencies
COPY --from=builder /app/docker/agents/target/quorus-agent-*.jar app.jar
COPY --from=builder /app/docker/agents/target/lib/ lib/

# Copy configuration files
COPY docker/agents/config/ config/

# Create directories for logs and data
RUN mkdir -p /app/logs /app/data /app/transfers && \
    chown -R quorus:quorus /app

# Switch to non-root user
USER quorus

# Expose agent communication port
EXPOSE 8080

# Environment variables for Quorus Agent
ENV JAVA_OPTS="-Xmx256m -Xms128m" \
    AGENT_ID="" \
    AGENT_REGION="default" \
    AGENT_DATACENTER="default" \
    CONTROLLER_URL="http://localhost:8080/api/v1" \
    SUPPORTED_PROTOCOLS="HTTP,HTTPS" \
    MAX_CONCURRENT_TRANSFERS=5 \
    HEARTBEAT_INTERVAL=30000 \
    AGENT_PORT=8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:${AGENT_PORT}/health || exit 1

# Entry point
COPY docker/agents/docker-entrypoint.sh /docker-entrypoint.sh
USER root
RUN chmod +x /docker-entrypoint.sh
USER quorus

ENTRYPOINT ["/docker-entrypoint.sh"]
