#!/bin/sh
# Executes the real agent entrypoint with stub curl and java commands, and checks which controller
# health URL it waits for and which TLS options it presents (register item ENG-17).
set -eu
entrypoint=${1:-$(dirname "$0")/../quorus-agent/docker-entrypoint.sh}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

mkdir -p "$work/bin"
cat > "$work/bin/curl" <<'EOF'
#!/bin/sh
echo "$*" >> "$CURL_LOG"
exit 0
EOF
cat > "$work/bin/java" <<'EOF'
#!/bin/sh
exit 0
EOF
cat > "$work/bin/sleep" <<'EOF'
#!/bin/sh
exit 0
EOF
chmod +x "$work/bin/curl" "$work/bin/java" "$work/bin/sleep"

run() {
  : > "$work/curl.log"
  env -i PATH="$work/bin:$PATH" CURL_LOG="$work/curl.log" "$@" sh "$entrypoint" > "$work/out.log" 2>&1
}

fail() {
  echo "FAIL: $1"
  echo "--- curl calls"; cat "$work/curl.log"
  echo "--- entrypoint output"; cat "$work/out.log"
  exit 1
}

run AGENT_ID=a1 CONTROLLER_URL=http://controller1:8080/api/v1 || fail 'plain HTTP controller'
grep -q 'http://controller1:8080/health/live' "$work/curl.log" || fail 'probe must target the root /health/live, not the API base'
echo 'PASS: probes the controller root health path'

run AGENT_ID=a1 QUORUS_AGENT_CONTROLLER_URL=http://canonical:8080/api/v1 CONTROLLER_URL=http://legacy:8080/api/v1 \
  || fail 'canonical controller URL'
grep -q 'http://canonical:8080/health/live' "$work/curl.log" || fail 'QUORUS_AGENT_CONTROLLER_URL must win over CONTROLLER_URL'
echo 'PASS: the canonical controller URL wins'

run QUORUS_AGENT_ID=a1 QUORUS_AGENT_CONTROLLER_URL=https://controller:8443/api/v1/ \
  QUORUS_AGENT_TLS_CERTIFICATE=/tls/agent.crt QUORUS_AGENT_TLS_PRIVATE_KEY=/tls/agent.key \
  QUORUS_AGENT_TLS_TRUST_BUNDLE=/tls/ca.crt || fail 'TLS controller'
grep -q 'https://controller:8443/health/live' "$work/curl.log" || fail 'trailing slash after /api/v1 must be handled'
grep -q -- '--cert /tls/agent.crt' "$work/curl.log" || fail 'a TLS probe must present the agent certificate'
grep -q -- '--key /tls/agent.key' "$work/curl.log" || fail 'a TLS probe must present the agent key'
grep -q -- '--cacert /tls/ca.crt' "$work/curl.log" || fail 'a TLS probe must trust the configured bundle'
echo 'PASS: a TLS probe presents the agent identity'

if run AGENT_ID=a1; then
  fail 'a missing controller URL must be rejected'
fi
echo 'PASS: missing controller URL rejected'
