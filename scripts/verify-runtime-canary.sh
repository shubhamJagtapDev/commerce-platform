#!/usr/bin/env bash
set -euo pipefail

workspace_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$workspace_dir"

canary="gate1-$(openssl rand -hex 24)"

curl --fail --silent --output /dev/null \
  -H "X-Test-Secret: $canary" \
  http://localhost:8080/api/v1/foundation
curl --fail --silent --output /dev/null \
  -H "X-Test-Secret: $canary" \
  http://localhost:8081/api/v1/foundation

if docker compose --env-file .env -f deployment/local/compose.yaml logs --no-color \
    identity-access-service catalog-service | grep -Fq "$canary"; then
  echo "Runtime canary leaked into service logs." >&2
  exit 1
fi

echo "Runtime secret-canary telemetry check: PASS"
