# Quorus Agent runtime image.
#
# The image packages the agent jar and its dependency jars built and tested on the host; it never
# runs Maven or compiles Java. Build them first, then the image:
#
#   docker/build-runtime.sh            (or docker/build-runtime.ps1)
#   docker build -f quorus-agent/Dockerfile -t quorus-agent .
#
# Runtime: Amazon Corretto 27 on Alpine (ADR-0012, decision RT-Q4), pinned to an exact release.

ARG RUNTIME_IMAGE=amazoncorretto:27.0.0-alpine3.24
FROM ${RUNTIME_IMAGE}

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
COPY quorus-agent/target/quorus-agent-*.jar app.jar
COPY quorus-agent/target/lib/ lib/

# Copy configuration files from resources
COPY quorus-agent/src/main/resources/logback.xml config/logback.xml

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
COPY quorus-agent/docker-entrypoint.sh /docker-entrypoint.sh
USER root
RUN chmod +x /docker-entrypoint.sh
USER quorus

ENTRYPOINT ["/docker-entrypoint.sh"]
