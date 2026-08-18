#!/usr/bin/env bash
set -euo pipefail

workspace_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$workspace_dir"

if find . -path './.git' -prune -o -path './.gradle' -prune -o -name '.env' -print | grep -q .; then
  echo "A local .env file exists; ensure it remains ignored and never include it in evidence." >&2
fi

if [[ -d .git ]] && ! git check-ignore --quiet .env; then
  echo ".env is not ignored by Git." >&2
  exit 1
fi

set +e
grep -R -n -E \
  --exclude=.env.example \
  '(BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY|AKIA[0-9A-Z]{16}|password:[[:space:]]+[A-Za-z0-9+/=_-]{12,}|client-secret:[[:space:]]+[A-Za-z0-9+/=_-]{12,})' \
  services/identity-access-service/src \
  services/catalog-service/src \
  services/identity-access-service/Dockerfile \
  services/catalog-service/Dockerfile \
  contracts deployment/local scripts .github gradle docs \
  README.md build.gradle.kts settings.gradle.kts gradle.properties dev
grep_status=$?
set -e

if [[ $grep_status -eq 0 ]]; then
  echo "Possible committed secret found." >&2
  exit 1
fi

if [[ $grep_status -gt 1 ]]; then
  echo "Committed-secret scan could not inspect every configured path." >&2
  exit "$grep_status"
fi

echo "Committed-secret heuristic: PASS"
