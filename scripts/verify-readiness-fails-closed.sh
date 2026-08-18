#!/usr/bin/env bash
set -euo pipefail

workspace_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$workspace_dir"
compose=(docker compose --env-file .env -f deployment/local/compose.yaml)

restore_postgres() {
  "${compose[@]}" start postgres >/dev/null
}
trap restore_postgres EXIT

"${compose[@]}" stop postgres >/dev/null

for endpoint in \
  http://localhost:8080/actuator/health/liveness \
  http://localhost:8081/actuator/health/liveness; do
  curl --fail --silent --max-time 3 "$endpoint" >/dev/null
done

for endpoint in \
  http://localhost:8080/actuator/health/readiness \
  http://localhost:8081/actuator/health/readiness; do
  observed=""
  for attempt in {1..20}; do
    observed="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 5 "$endpoint" || true)"
    [[ "$observed" == "503" ]] && break
    sleep 1
  done
  if [[ "$observed" != "503" ]]; then
    echo "Readiness did not fail closed for $endpoint; last status was $observed" >&2
    exit 1
  fi
done

restore_postgres
trap - EXIT

postgres_id="$("${compose[@]}" ps -q postgres)"
for attempt in {1..30}; do
  [[ "$(docker inspect --format '{{.State.Health.Status}}' "$postgres_id")" == "healthy" ]] && break
  sleep 1
done

[[ "$(docker inspect --format '{{.State.Health.Status}}' "$postgres_id")" == "healthy" ]]

for endpoint in \
  http://localhost:8080/actuator/health/readiness \
  http://localhost:8081/actuator/health/readiness; do
  observed=""
  for attempt in {1..20}; do
    observed="$(curl --silent --output /dev/null --write-out '%{http_code}' --max-time 5 "$endpoint" || true)"
    [[ "$observed" == "200" ]] && break
    sleep 1
  done
  if [[ "$observed" != "200" ]]; then
    echo "Readiness did not recover for $endpoint; last status was $observed" >&2
    exit 1
  fi
done

echo "Owned-database readiness failure and recovery: PASS"
