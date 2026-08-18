#!/usr/bin/env bash
set -euo pipefail

workspace_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$workspace_dir"

for contract_file in contracts/openapi/*.yaml; do
  grep -q -E '^openapi: 3\.1\.0$' "$contract_file"
  grep -q -E '^paths:$' "$contract_file"
  grep -q -E 'version:' "$contract_file"
done

for event_file in contracts/events/*.json; do
  grep -q -F '"$schema"' "$event_file"
  grep -q -F '"$id"' "$event_file"
  grep -q -E '"type":[[:space:]]*"object"' "$event_file"
  grep -q -F '"required"' "$event_file"
done

echo "Versioned contract sources: PASS"
