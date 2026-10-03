#!/bin/sh
# Executes the real agent entrypoint with stub curl and java commands, and checks that it validates
# its required settings and starts the agent without waiting for a controller: the agent itself
# retries registration and follows the leader among its configured controllers (register items
# ENG-17 and ENG-27).
set -eu
entrypoint=${1:-$(dirname "$0")/../quorus-agent/docker-entrypoint.sh}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

mkdir -p "$work/bin"
# No controller is reachable: every probe fails.
cat > "$work/bin/curl" <<'EOF'
#!/bin/sh
echo "$*" >> "$CURL_LOG"
exit 7
EOF
cat > "$work/bin/java" <<'EOF'
#!/bin/sh
echo "started" >> "$JAVA_LOG"
env >> "$JAVA_LOG"
exit 0
EOF
cat > "$work/bin/sleep" <<'EOF'
#!/bin/sh
exit 0
EOF
chmod +x "$work/bin/curl" "$work/bin/java" "$work/bin/sleep"

run() {
  : > "$work/curl.log"
  : > "$work/java.log"
  env -i PATH="$work/bin:$PATH" CURL_LOG="$work/curl.log" JAVA_LOG="$work/java.log" "$@" \
    sh "$entrypoint" > "$work/out.log" 2>&1
}

fail() {
  echo "FAIL: $1"
  echo "--- curl calls"; cat "$work/curl.log"
  echo "--- entrypoint output"; cat "$work/out.log"
  exit 1
}

run AGENT_ID=a1 CONTROLLER_URL=http://controller1:8080/api/v1,http://controller2:8080/api/v1 \
  || fail 'the agent must start although no controller answers'
grep -q started "$work/java.log" || fail 'the agent was not started'
[ -s "$work/curl.log" ] && fail 'the entrypoint must not probe the controller; the agent retries registration itself'
echo 'PASS: starts the agent without waiting for a controller'

grep -q '^AGENT_VERSION=' "$work/java.log" && fail 'the product version is not a setting; AGENT_VERSION must not be exported (DR-Q5)'
echo 'PASS: exports no agent version'

run AGENT_ID=a1 QUORUS_AGENT_CONTROLLER_URL=http://canonical:8080/api/v1 CONTROLLER_URL=http://legacy:8080/api/v1 \
  || fail 'canonical controller URL'
grep -q 'Controller URL: http://canonical:8080/api/v1' "$work/out.log" \
  || fail 'QUORUS_AGENT_CONTROLLER_URL must win over CONTROLLER_URL'
echo 'PASS: the canonical controller URL wins'

if run AGENT_ID=a1; then
  fail 'a missing controller URL must be rejected'
fi
grep -q started "$work/java.log" && fail 'the agent must not start without a controller URL'
echo 'PASS: missing controller URL rejected'

if run CONTROLLER_URL=http://controller1:8080/api/v1; then
  fail 'a missing agent ID must be rejected'
fi
echo 'PASS: missing agent ID rejected'
