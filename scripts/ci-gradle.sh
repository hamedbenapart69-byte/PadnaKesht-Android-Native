#!/usr/bin/env bash
# Usage: ci-gradle.sh <log-name> <gradle args...>
# Runs Gradle, keeps the output, and turns a failure into GitHub annotations.
set +e
name="$1"; shift
mkdir -p logs
gradle "$@" --stacktrace 2>&1 | tee "logs/$name.log"
rc=${PIPESTATUS[0]}
if [ "$rc" -ne 0 ]; then
  python3 scripts/ci_report.py --gradle-log "logs/$name.log"
fi
exit "$rc"
