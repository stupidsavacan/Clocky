#!/usr/bin/env bash
set -euo pipefail

class_name="${1:?class name required}"
report="app/build/test-results/testDebugUnitTest/TEST-${class_name}.xml"

if [[ ! -f "$report" ]]; then
  echo "Missing JUnit report: $report"
  exit 2
fi

summary="$(head -n 2 "$report" | tail -n 1)"
echo "$summary"

if grep -Eq 'failures="[1-9][0-9]*"|errors="[1-9][0-9]*"' "$report"; then
  echo "JUnit failures detected in $class_name"
  grep -E '<failure|<error|<testcase' "$report" || true
  exit 1
fi

echo "$class_name: no failures/errors"
