# Quorus Controller runtime image.
#
# The image packages the controller jar built and tested on the host; it never runs Maven or
# compiles Java. Build the jar first, then the image:
#
#   docker/build-runtime.sh            (or docker/build-runtime.ps1)
#   docker build -f quorus-controller/Dockerfile -t quorus-controller .
#
# Runtime: Amazon Corretto 27 on Alpine (ADR-0012, decision RT-Q4). The tag is pinned to an exact
# Corretto release; plan item RT-09 moves it with each Java release.

ARG RUNTIME_IMAGE=amazoncorretto:27.0.0-alpine3.24
FROM ${RUNTIME_IMAGE}

# Install curl for health checks
RUN apk add --no-cache curl

# Create app user
RUN addgroup -g 1001 quorus && \
    adduser -D -s /bin/sh -u 1001 -G quorus quorus

# Set working directory
WORKDIR /app

# Copy the built JAR
COPY quorus-controller/target/quorus-controller-*.jar app.jar

# Create directories for logs and data
RUN mkdir -p /app/logs /app/data && \
    chown -R quorus:quorus /app

# Switch to non-root user
USER quorus

# Expose the HTTP API and Raft communication ports
EXPOSE 8080 9080

# Environment variables for Quorus Controller
ENV JAVA_OPTS="-Xmx512m -Xms256m" \
    QUORUS_NODE_ID="" \
    QUORUS_CLUSTER_NODES="" \
    QUORUS_RAFT_PORT=9080 \
    QUORUS_HTTP_HOST=0.0.0.0 \
    QUORUS_HTTP_PORT=8080 \
    QUORUS_RAFT_ELECTION_TIMEOUT_MS=5000 \
    QUORUS_RAFT_HEARTBEAT_INTERVAL_MS=1000 \
    QUORUS_RAFT_STORAGE_PATH=/app/data/raft

# Health check follows the configured HTTP transport. The local HTTPS probe still
# presents a client certificate because production HTTP requires mutual TLS. A
# deployment may provide a dedicated probe identity through the two health-check
# variables; otherwise the controller certificate/key are used.
HEALTHCHECK --interval=10s --timeout=5s --start-period=15s --retries=3 \
    CMD if [ "${QUORUS_SECURITY_HTTP_TLS_ENABLED:-true}" = "true" ]; then \
      curl --fail --silent --show-error --insecure \
        --cert "${QUORUS_HEALTHCHECK_CLIENT_CERTIFICATE:-$QUORUS_SECURITY_HTTP_TLS_CERTIFICATE}" \
        --key "${QUORUS_HEALTHCHECK_CLIENT_PRIVATE_KEY:-$QUORUS_SECURITY_HTTP_TLS_PRIVATE_KEY}" \
        "https://localhost:${QUORUS_HTTP_PORT}/health/live"; \
      else \
        curl --fail --silent --show-error "http://localhost:${QUORUS_HTTP_PORT}/health/live"; \
      fi

# Run the Quorus Controller application
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
