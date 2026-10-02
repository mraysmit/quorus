#!/usr/bin/env sh
# Builds the Quorus runtime jars on the host. Container images only package these artifacts;
# nothing is compiled inside Docker.
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPOSITORY_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)

echo "Building the Quorus controller and agent jars on the host with Maven..."
mvn -B -f "$REPOSITORY_ROOT/pom.xml" clean package -pl quorus-controller,quorus-agent -am -DskipTests

for artifact in \
    "$REPOSITORY_ROOT/quorus-controller/target/quorus-controller-1.0-SNAPSHOT.jar" \
    "$REPOSITORY_ROOT/quorus-agent/target/quorus-agent-1.0-SNAPSHOT.jar"; do
  if [ ! -f "$artifact" ]; then
    echo "Expected host-built artifact was not created: $artifact" >&2
    exit 1
  fi
  echo "Host-built artifact: $artifact"
done
