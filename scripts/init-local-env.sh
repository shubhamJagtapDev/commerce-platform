#!/usr/bin/env bash
set -euo pipefail

workspace_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="$workspace_dir/.env"

if [[ -f "$env_file" ]]; then
  exit 0
fi

postgres_admin_password="$(openssl rand -hex 24)"
identity_db_password="$(openssl rand -hex 24)"
catalog_db_password="$(openssl rand -hex 24)"
keycloak_db_password="$(openssl rand -hex 24)"
keycloak_admin_password="$(openssl rand -hex 24)"
identity_oidc_client_secret="$(openssl rand -hex 32)"

umask 077
{
  printf 'POSTGRES_ADMIN_PASSWORD=%s\n' "$postgres_admin_password"
  printf 'IDENTITY_DB_USER=identity_access_app\n'
  printf 'IDENTITY_DB_PASSWORD=%s\n' "$identity_db_password"
  printf 'CATALOG_DB_USER=catalog_app\n'
  printf 'CATALOG_DB_PASSWORD=%s\n' "$catalog_db_password"
  printf 'KEYCLOAK_DB_USER=keycloak_app\n'
  printf 'KEYCLOAK_DB_PASSWORD=%s\n' "$keycloak_db_password"
  printf 'KEYCLOAK_ADMIN_USER=local_admin\n'
  printf 'KEYCLOAK_ADMIN_PASSWORD=%s\n' "$keycloak_admin_password"
  printf 'IDENTITY_OIDC_CLIENT_SECRET=%s\n' "$identity_oidc_client_secret"
} > "$env_file"

echo "Generated secret-safe local overrides at .env (mode 600, git-ignored)."
