#!/bin/sh

# Quorus Controller Docker Entrypoint Script
# Configures and starts the Raft controller node

set -e

# Default values
DEFAULT_NODE_ID="controller1"
DEFAULT_RAFT_PORT=8080
DEFAULT_RAFT_HOST="0.0.0.0"
DEFAULT_ELECTION_TIMEOUT_MS=5000
DEFAULT_HEARTBEAT_INTERVAL_MS=1000

# Set defaults if not provided
NODE_ID=${NODE_ID:-$DEFAULT_NODE_ID}
RAFT_PORT=${RAFT_PORT:-$DEFAULT_RAFT_PORT}
RAFT_HOST=${RAFT_HOST:-$DEFAULT_RAFT_HOST}
ELECTION_TIMEOUT_MS=${ELECTION_TIMEOUT_MS:-$DEFAULT_ELECTION_TIMEOUT_MS}
HEARTBEAT_INTERVAL_MS=${HEARTBEAT_INTERVAL_MS:-$DEFAULT_HEARTBEAT_INTERVAL_MS}

echo "Starting Quorus Controller..."
echo "Node ID: $NODE_ID"
echo "Raft Host: $RAFT_HOST"
echo "Raft Port: $RAFT_PORT"
echo "Election Timeout: ${ELECTION_TIMEOUT_MS}ms"
echo "Heartbeat Interval: ${HEARTBEAT_INTERVAL_MS}ms"

# Validate required environment variables
if [ -z "$CLUSTER_NODES" ]; then
    echo "ERROR: CLUSTER_NODES environment variable is required"
    echo "Format: node1=host1:port1,node2=host2:port2,node3=host3:port3"
    exit 1
fi

echo "Cluster Nodes: $CLUSTER_NODES"

# Wait for other nodes to be available (basic readiness check)
echo "Checking cluster node availability..."
IFS=',' read -ra NODES <<< "$CLUSTER_NODES"
for node in "${NODES[@]}"; do
    node_name=$(echo "$node" | cut -d'=' -f1)
    node_address=$(echo "$node" | cut -d'=' -f2)
    node_host=$(echo "$node_address" | cut -d':' -f1)
    node_port=$(echo "$node_address" | cut -d':' -f2)
    
    # Skip self
    if [ "$node_name" = "$NODE_ID" ]; then
        continue
    fi
    
    echo "Waiting for $node_name at $node_host:$node_port..."
    timeout=60
    while [ $timeout -gt 0 ]; do
        if nc -z "$node_host" "$node_port" 2>/dev/null; then
            echo "$node_name is available"
            break
        fi
        sleep 2
        timeout=$((timeout - 2))
    done
    
    if [ $timeout -le 0 ]; then
        echo "WARNING: $node_name at $node_host:$node_port is not available after 60 seconds"
    fi
done

# Set Java system properties
JAVA_OPTS="$JAVA_OPTS -Dquorus.raft.nodeId=$NODE_ID"
JAVA_OPTS="$JAVA_OPTS -Dquorus.raft.host=$RAFT_HOST"
JAVA_OPTS="$JAVA_OPTS -Dquorus.raft.port=$RAFT_PORT"
JAVA_OPTS="$JAVA_OPTS -Dquorus.raft.clusterNodes=$CLUSTER_NODES"
JAVA_OPTS="$JAVA_OPTS -Dquorus.raft.electionTimeoutMs=$ELECTION_TIMEOUT_MS"
JAVA_OPTS="$JAVA_OPTS -Dquorus.raft.heartbeatIntervalMs=$HEARTBEAT_INTERVAL_MS"

# Logging configuration
JAVA_OPTS="$JAVA_OPTS -Djava.util.logging.config.file=/app/logging.properties"

# JVM tuning for containers
JAVA_OPTS="$JAVA_OPTS -XX:+UseContainerSupport"
JAVA_OPTS="$JAVA_OPTS -XX:MaxRAMPercentage=75.0"
JAVA_OPTS="$JAVA_OPTS -XX:+UseG1GC"
JAVA_OPTS="$JAVA_OPTS -XX:+UseStringDeduplication"

export JAVA_OPTS

echo "Java Options: $JAVA_OPTS"
echo "Starting application..."

# Execute the command
exec "$@"
