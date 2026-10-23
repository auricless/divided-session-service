#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
npm --prefix frontend ci --ignore-scripts
npm --prefix frontend run build
maven_options=()
if [[ -n "${MAVEN_SETTINGS:-}" ]]; then
  maven_options+=(-s "$MAVEN_SETTINGS")
fi
mvn --batch-mode --no-transfer-progress "${maven_options[@]}" clean verify "-P${MAVEN_PROFILES:-web-app}"
test -s target/app.jar
docker build --tag divided-session-service:local .
