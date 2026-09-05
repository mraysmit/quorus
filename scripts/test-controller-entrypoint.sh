#!/bin/sh
# Executes the real entrypoint and inspects only synthetic configuration values.
set -eu
entrypoint=${1:-/workspace/quorus-controller/docker-entrypoint.sh}

env -i PATH="$PATH" QUORUS_NODE_ID=canonical QUORUS_CLUSTER_NODES=canonical=localhost:9080 \
  QUORUS_RAFT_PORT=19080 QUORUS_RAFT_ELECTION_TIMEOUT_MS=6000 QUORUS_RAFT_HEARTBEAT_INTERVAL_MS=1200 \
  sh "$entrypoint" sh -c '
    test "$QUORUS_NODE_ID" = canonical && test "$QUORUS_RAFT_PORT" = 19080 &&
    test "$QUORUS_CLUSTER_NODES" = canonical=localhost:9080 &&
    test "$QUORUS_RAFT_ELECTION_TIMEOUT_MS" = 6000 && test "$QUORUS_RAFT_HEARTBEAT_INTERVAL_MS" = 1200
  '
echo 'PASS: canonical-only environment'

env -i PATH="$PATH" QUORUS_NODE_ID=canonical NODE_ID=legacy \
  QUORUS_CLUSTER_NODES=canonical=localhost:9080 CLUSTER_NODES=legacy=localhost:9080 \
  QUORUS_RAFT_PORT=19080 RAFT_PORT=29080 \
  QUORUS_RAFT_ELECTION_TIMEOUT_MS=6000 ELECTION_TIMEOUT_MS=7000 \
  QUORUS_RAFT_HEARTBEAT_INTERVAL_MS=1200 HEARTBEAT_INTERVAL_MS=1500 \
  sh "$entrypoint" sh -c '
    test "$QUORUS_NODE_ID" = canonical && test "$QUORUS_RAFT_PORT" = 19080 &&
    test "$QUORUS_CLUSTER_NODES" = canonical=localhost:9080 &&
    test "$QUORUS_RAFT_ELECTION_TIMEOUT_MS" = 6000 && test "$QUORUS_RAFT_HEARTBEAT_INTERVAL_MS" = 1200
  '
echo 'PASS: canonical environment wins over legacy'

env -i PATH="$PATH" NODE_ID=legacy CLUSTER_NODES=legacy=localhost:9080 \
  sh "$entrypoint" sh -c 'test "$QUORUS_NODE_ID" = legacy && test "$QUORUS_RAFT_PORT" = 9080'
echo 'PASS: legacy fallback'

if env -i PATH="$PATH" sh "$entrypoint" true; then
  echo 'FAIL: missing cluster accepted'; exit 1
fi
echo 'PASS: missing cluster rejected'
