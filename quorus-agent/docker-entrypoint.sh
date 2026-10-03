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
export AGENT_VERSION=${AGENT_VERSION:-1.0.0}

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

# Wait for the controller, because the agent stops if its first registration fails. The controller
# serves health at its root, not under the API base, so strip /api/v1 from the controller URL. A TLS
# controller requires a client certificate at the handshake, so present the agent's own identity.
controller_base="${controller_url%/}"
controller_base="${controller_base%/api/v1}"
health_url="$controller_base/health/live"
set --
case "$controller_base" in
    https://*)
        [ -n "${QUORUS_AGENT_TLS_TRUST_BUNDLE:-}" ] && set -- "$@" --cacert "$QUORUS_AGENT_TLS_TRUST_BUNDLE"
        [ -n "${QUORUS_AGENT_TLS_CERTIFICATE:-}" ] && set -- "$@" --cert "$QUORUS_AGENT_TLS_CERTIFICATE"
        [ -n "${QUORUS_AGENT_TLS_PRIVATE_KEY:-}" ] && set -- "$@" --key "$QUORUS_AGENT_TLS_PRIVATE_KEY"
        ;;
esac
echo "Waiting for controller to be available at $health_url..."
timeout=60
counter=0
while ! curl -fsS --max-time 5 "$@" "$health_url" >/dev/null 2>&1; do
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
