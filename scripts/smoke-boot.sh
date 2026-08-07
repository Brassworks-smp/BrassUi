#!/usr/bin/env bash
# Boot smoke test: launches a Gradle game run, waits for the game to finish starting, then kills it.
# Exits 0 only when the startup marker appears in the log before the timeout — so CI knows the mod
# genuinely boots on each version/loader it builds.
#
# Usage:
#   scripts/smoke-boot.sh <gradle-task> <server|client> [timeout-seconds]
#
#   server  — dedicated server; greps for the NeoForge "Done" line (writes run/eula.txt first).
#   client  — game client; greps for the "BrassUi loaded" line BrassUiMod logs at construction.
#             Run it under xvfb in headless CI: xvfb-run -a scripts/smoke-boot.sh :mod:runClient client
set -euo pipefail

TASK="${1:?usage: smoke-boot.sh <gradle-task> <server|client> [timeout-seconds]}"
MODE="${2:?usage: smoke-boot.sh <gradle-task> <server|client> [timeout-seconds]}"
TIMEOUT="${3:-600}"
LOG="build/smoke-$MODE.log"

case "$MODE" in
  server) MARKER='Done (' ;;
  client) MARKER='BrassUi loaded' ;;
  *) echo "unknown mode: $MODE (expected server or client)" >&2; exit 2 ;;
esac

mkdir -p build run
: > "$LOG"
if [[ "$MODE" == "server" && ! -f run/eula.txt ]]; then
  # Throwaway dev-run EULA acceptance (CI runners and local dev runs only — never shipped).
  echo "eula=true" > run/eula.txt
fi

# --no-daemon so the game process dies with this script's gradle client when we kill it.
./gradlew --no-daemon "$TASK" --console=plain > "$LOG" 2>&1 &
GRADLE_PID=$!

start=$(date +%s)
while true; do
  if grep -q "$MARKER" "$LOG" 2>/dev/null || grep -q "$MARKER" run/logs/latest.log 2>/dev/null; then
    echo "OK: $MODE booted in $(($(date +%s) - start))s (marker: $MARKER)"
    kill "$GRADLE_PID" 2>/dev/null || true
    exit 0
  fi
  if ! kill -0 "$GRADLE_PID" 2>/dev/null; then
    echo "FAIL: $TASK exited before the marker appeared. Log tail:" >&2
    tail -40 "$LOG" >&2
    exit 1
  fi
  if (($(date +%s) - start > TIMEOUT)); then
    echo "FAIL: no marker after ${TIMEOUT}s. Log tail:" >&2
    tail -40 "$LOG" >&2
    kill "$GRADLE_PID" 2>/dev/null || true
    exit 1
  fi
  sleep 2
done
