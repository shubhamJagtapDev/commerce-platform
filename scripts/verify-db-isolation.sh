#!/usr/bin/env bash
set -euo pipefail

workspace_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$workspace_dir"
source .env

if docker compose --env-file .env -f deployment/local/compose.yaml exec -T \
  -e PGPASSWORD="$IDENTITY_DB_PASSWORD" postgres \
  psql -h 127.0.0.1 -U "$IDENTITY_DB_USER" -d catalog -c 'select 1' >/dev/null 2>&1; then
  echo "Identity Access credentials connected to the Catalog database." >&2
  exit 1
fi

if docker compose --env-file .env -f deployment/local/compose.yaml exec -T \
  -e PGPASSWORD="$CATALOG_DB_PASSWORD" postgres \
  psql -h 127.0.0.1 -U "$CATALOG_DB_USER" -d identity_access -c 'select 1' >/dev/null 2>&1; then
  echo "Catalog credentials connected to the Identity Access database." >&2
  exit 1
fi

echo "Cross-database credentials: DENIED"
