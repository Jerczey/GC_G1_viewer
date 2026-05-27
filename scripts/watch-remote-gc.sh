#!/usr/bin/env bash
# Stream a remote RH-SSO / JBoss GC log to a local file, then open GC G1 Viewer in watch mode.
#
# Usage:
#   ./scripts/watch-remote-gc.sh admin@rhsso-lab-1 /opt/rhsso/standalone/log/gc-2026-05-27_12-14-33.log
#   ./scripts/watch-remote-gc.sh admin@rhsso-lab-1 /opt/rhsso/standalone/log/gc.log
#
# - tail -F follows the file even if JBoss rotates to a new gc-*.log (same path).
# - Press Ctrl+C to stop streaming; the viewer keeps the last local copy.

set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "Usage: $0 USER@HOST REMOTE_GC_LOG [LOCAL_COPY]" >&2
  echo "Example: $0 admin@rhsso-lab-1 /opt/rhsso/standalone/log/gc.log ~/gc-live.log" >&2
  exit 1
fi

SSH_TARGET="$1"
REMOTE_PATH="$2"
LOCAL_COPY="${3:-${HOME}/gc-live-rhsso.log}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="${ROOT}/target/gc-g1-viewer.jar"

echo "Streaming ${SSH_TARGET}:${REMOTE_PATH} -> ${LOCAL_COPY}"
echo "Stop streaming with Ctrl+C (viewer can stay open)."
echo

: > "${LOCAL_COPY}"

cleanup() {
  [[ -n "${SSH_PID:-}" ]] && kill "${SSH_PID}" 2>/dev/null || true
}
trap cleanup EXIT INT TERM

# -F = follow by name (handles log rotation if the path is recreated)
ssh -o ServerAliveInterval=30 "${SSH_TARGET}" "tail -n +1 -F '${REMOTE_PATH}'" >> "${LOCAL_COPY}" &
SSH_PID=$!

if [[ -f "${JAR}" ]]; then
  java -jar "${JAR}" --watch "${LOCAL_COPY}" &
  VIEWER_PID=$!
  sleep 1
  echo "Viewer PID ${VIEWER_PID} (watch mode). File: ${LOCAL_COPY}"
else
  echo "Build the viewer first: mvn -q package"
  echo "Then run: java -jar target/gc-g1-viewer.jar"
  echo "Open / Watch Live on: ${LOCAL_COPY}"
fi

wait "${SSH_PID}"
