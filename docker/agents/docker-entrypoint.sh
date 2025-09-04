#!/bin/bash

# Quorus Agent Docker Entry Point
# This script configures and starts the Quorus Agent

set -e

echo "Starting Quorus Agent..."
echo "Agent ID: ${AGENT_ID:-not-set}"
echo "Region: ${AGENT_REGION:-default}"
echo "Datacenter: ${AGENT_DATACENTER:-default}"
echo "Controller URL: ${CONTROLLER_URL:-http://localhost:8080/api/v1}"

# Validate required environment variables
if [ -z "$AGENT_ID" ]; then
    echo "ERROR: AGENT_ID environment variable is required"
    exit 1
fi

if [ -z "$CONTROLLER_URL" ]; then
    echo "ERROR: CONTROLLER_URL environment variable is required"
    exit 1
fi

# Set default values for optional variables
export AGENT_REGION=${AGENT_REGION:-default}
export AGENT_DATACENTER=${AGENT_DATACENTER:-default}
export SUPPORTED_PROTOCOLS=${SUPPORTED_PROTOCOLS:-HTTP,HTTPS}
export MAX_CONCURRENT_TRANSFERS=${MAX_CONCURRENT_TRANSFERS:-5}
export HEARTBEAT_INTERVAL=${HEARTBEAT_INTERVAL:-30000}
export AGENT_PORT=${AGENT_PORT:-8080}
export AGENT_VERSION=${AGENT_VERSION:-1.0.0}

# Configure Java options
JAVA_OPTS="${JAVA_OPTS:-} -Dquorus.agent.id=$AGENT_ID"
JAVA_OPTS="$JAVA_OPTS -Dquorus.agent.region=$AGENT_REGION"
JAVA_OPTS="$JAVA_OPTS -Dquorus.agent.datacenter=$AGENT_DATACENTER"
JAVA_OPTS="$JAVA_OPTS -Dquorus.controller.url=$CONTROLLER_URL"

# JVM tuning for containers
JAVA_OPTS="$JAVA_OPTS -XX:+UseContainerSupport"
JAVA_OPTS="$JAVA_OPTS -XX:MaxRAMPercentage=75.0"
JAVA_OPTS="$JAVA_OPTS -XX:+UseG1GC"
JAVA_OPTS="$JAVA_OPTS -XX:+UseStringDeduplication"

# Logging configuration
JAVA_OPTS="$JAVA_OPTS -Dlogback.configurationFile=/app/config/logback.xml"

export JAVA_OPTS

echo "Java Options: $JAVA_OPTS"
echo "Supported Protocols: $SUPPORTED_PROTOCOLS"
echo "Max Concurrent Transfers: $MAX_CONCURRENT_TRANSFERS"
echo "Heartbeat Interval: ${HEARTBEAT_INTERVAL}ms"

# Wait for controller to be available
echo "Waiting for controller to be available..."
timeout=60
counter=0
while ! curl -f "$CONTROLLER_URL/health" >/dev/null 2>&1; do
    if [ $counter -ge $timeout ]; then
        echo "ERROR: Controller not available after ${timeout} seconds"
        exit 1
    fi
    echo "Controller not ready, waiting... ($counter/$timeout)"
    sleep 1
    counter=$((counter + 1))
done

echo "Controller is available, starting agent..."

# Start the agent
exec java $JAVA_OPTS -jar app.jar
