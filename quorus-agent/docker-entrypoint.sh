#!/bin/bash

# Quorus Agent Docker Entry Point
# This script configures and starts the Quorus Agent

set -e

# The documented QUORUS_AGENT_* names win over the legacy unprefixed names, as in AgentConfig.
agent_id="${QUORUS_AGENT_ID:-${AGENT_ID:-}}"
controller_url="${QUORUS_AGENT_CONTROLLER_URL:-${CONTROLLER_URL:-}}"

echo "Starting Quorus Agent..."
echo "Agent ID: ${agent_id:-not-set}"
echo "Region: ${AGENT_REGION:-default}"
echo "Datacenter: ${AGENT_DATACENTER:-default}"
echo "Controller URL: ${controller_url:-not-set}"

# Validate required environment variables
if [ -z "$agent_id" ]; then
    echo "ERROR: QUORUS_AGENT_ID (or legacy AGENT_ID) environment variable is required"
    exit 1
fi

if [ -z "$controller_url" ]; then
    echo "ERROR: QUORUS_AGENT_CONTROLLER_URL (or legacy CONTROLLER_URL) environment variable is required"
    exit 1
fi

# Set default values for optional variables
export AGENT_REGION=${AGENT_REGION:-default}
export AGENT_DATACENTER=${AGENT_DATACENTER:-default}
export SUPPORTED_PROTOCOLS=${SUPPORTED_PROTOCOLS:-HTTP,HTTPS}
export MAX_CONCURRENT_TRANSFERS=${MAX_CONCURRENT_TRANSFERS:-5}
export HEARTBEAT_INTERVAL=${HEARTBEAT_INTERVAL:-30000}
export AGENT_PORT=${AGENT_PORT:-8080}

# Agent configuration is supplied through exported environment values. JVM
# system properties are reserved for JVM and logging concerns only.
JAVA_OPTS="${JAVA_OPTS:-}"

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

# The entrypoint does not wait for a controller. The controller URL may list every controller of a
# cluster; the agent retries registration until one accepts it and follows the leader among them.

# Start the agent
exec java $JAVA_OPTS -jar app.jar
