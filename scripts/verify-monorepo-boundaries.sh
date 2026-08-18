#!/usr/bin/env bash
set -euo pipefail

workspace_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$workspace_dir"

if grep -R -n -F 'project(":services:' services --include='build.gradle.kts' \
    || grep -R -n -F "project(':services:" services --include='build.gradle.kts'; then
  echo "A service build depends on another service project; cross-service source dependencies are forbidden." >&2
  exit 1
fi

if grep -R -n 'com\.commerce\.catalog' services/identity-access-service/src --include='*.java'; then
  echo "Identity Access imports Catalog implementation code." >&2
  exit 1
fi

if grep -R -n 'com\.commerce\.identityaccess' services/catalog-service/src --include='*.java'; then
  echo "Catalog imports Identity Access implementation code." >&2
  exit 1
fi

test -d services/identity-access-service/src/main/resources/db/migration
test -d services/catalog-service/src/main/resources/db/migration

if grep -E '(spring-cloud-starter-loadbalancer|spring-cloud-starter-circuitbreaker|spring-data-redis|spring-session-(jdbc|data-redis))' \
    services/identity-access-service/gradle.lockfile; then
  echo "A forbidden discovery, circuit-breaker, Redis, or Spring Session implementation is locked." >&2
  exit 1
fi

if grep -R -n -E '(gateway_route|spring_session|oauth2_authorized_client)' \
    services/identity-access-service/src/main/resources/db/migration \
    services/catalog-service/src/main/resources/db/migration; then
  echo "A forbidden gateway route, Spring Session, or OAuth authorized-client table appears in service migrations." >&2
  exit 1
fi

echo "Monorepo service boundaries: PASS"
