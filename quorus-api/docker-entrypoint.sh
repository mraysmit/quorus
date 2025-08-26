#!/bin/sh

# Quorus API Docker Entrypoint Script
# Configures and starts the API service

set -e

# Default values
DEFAULT_HTTP_HOST="0.0.0.0"
DEFAULT_HTTP_PORT=8080

# Set defaults if not provided
QUARKUS_HTTP_HOST=${QUARKUS_HTTP_HOST:-$DEFAULT_HTTP_HOST}
QUARKUS_HTTP_PORT=${QUARKUS_HTTP_PORT:-$DEFAULT_HTTP_PORT}

echo "Starting Quorus API Service..."
echo "HTTP Host: $QUARKUS_HTTP_HOST"
echo "HTTP Port: $QUARKUS_HTTP_PORT"

# Wait for controller cluster to be available
if [ -n "$CONTROLLER_ENDPOINTS" ]; then
    echo "Controller Endpoints: $CONTROLLER_ENDPOINTS"
    echo "Waiting for controller cluster to be available..."

    # Use POSIX shell compatible approach
    OLD_IFS="$IFS"
    IFS=','
    for endpoint in $CONTROLLER_ENDPOINTS; do
        IFS="$OLD_IFS"
        # Extract host and port from http://host:port format
        host_port=$(echo "$endpoint" | sed 's|http://||' | sed 's|https://||')
        host=$(echo "$host_port" | cut -d':' -f1)
        port=$(echo "$host_port" | cut -d':' -f2)

        echo "Waiting for controller at $host:$port..."
        timeout=60
        while [ $timeout -gt 0 ]; do
            if nc -z "$host" "$port" 2>/dev/null; then
                echo "Controller at $host:$port is available"
                break
            fi
            sleep 2
            timeout=$((timeout - 2))
        done

        if [ $timeout -le 0 ]; then
            echo "WARNING: Controller at $host:$port is not available after 60 seconds"
        fi
        IFS=','
    done
    IFS="$OLD_IFS"
else
    echo "No controller endpoints specified, starting without cluster connectivity check"
fi

# Set Quarkus system properties
JAVA_OPTS="$JAVA_OPTS -Dquarkus.http.host=$QUARKUS_HTTP_HOST"
JAVA_OPTS="$JAVA_OPTS -Dquarkus.http.port=$QUARKUS_HTTP_PORT"

# Set controller endpoints if provided
if [ -n "$CONTROLLER_ENDPOINTS" ]; then
    JAVA_OPTS="$JAVA_OPTS -Dquorus.controller.endpoints=$CONTROLLER_ENDPOINTS"
fi

# JVM tuning for containers
JAVA_OPTS="$JAVA_OPTS -XX:+UseContainerSupport"
JAVA_OPTS="$JAVA_OPTS -XX:MaxRAMPercentage=75.0"
JAVA_OPTS="$JAVA_OPTS -XX:+UseG1GC"
JAVA_OPTS="$JAVA_OPTS -XX:+UseStringDeduplication"

# Quarkus optimizations
JAVA_OPTS="$JAVA_OPTS -Dquarkus.log.level=INFO"
JAVA_OPTS="$JAVA_OPTS -Dquarkus.log.console.enable=true"

export JAVA_OPTS

echo "Java Options: $JAVA_OPTS"
echo "Starting application..."

# Execute the command
exec "$@"
