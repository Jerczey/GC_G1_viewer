#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
JAR="target/gc-g1-viewer.jar"
if [[ ! -f "$JAR" ]]; then
  echo "Building gc-g1-viewer..."
  mvn -q clean package
fi
exec java -jar "$JAR" "$@"
