#!/usr/bin/env bash
set -euo pipefail

workspace_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$workspace_dir"

find settings.gradle.kts build.gradle.kts gradle services contracts deployment/local \
  -type f \
  ! -path '*/build/*' \
  ! -path 'deployment/local/generated/*' \
  ! -name '.env' \
  -print0 \
  | sort -z \
  | xargs -0 shasum -a 256

echo "platform.java=25"
echo "platform.gradle=9.6.1"
echo "platform.spring-boot=4.1.0"
echo "platform.spring-cloud=2025.1.2"
echo "platform.keycloak=26.7.0"
echo "platform.postgresql=18.4"

if curl --fail --silent --max-time 3 http://localhost:8080/actuator/gatewayRoutes >/dev/null 2>&1; then
  route_manifest="$(curl --fail --silent --max-time 3 http://localhost:8080/actuator/gatewayRoutes)"
  printf '%s' "$route_manifest" | shasum -a 256 | sed 's/  -$/  runtime.gateway-route-manifest.json/'
fi

if docker info >/dev/null 2>&1; then
  echo "environment.docker=$(docker version --format '{{.Server.Version}}')"
  echo "environment.arch=$(docker version --format '{{.Server.Arch}}')"
  docker compose --env-file .env -f deployment/local/compose.yaml config --images \
    | sort \
    | sed 's/^/environment.image=/'
fi
