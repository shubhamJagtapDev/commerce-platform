#!/usr/bin/env bash
set -euo pipefail

workspace_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="$workspace_dir/.env"

postgres_admin_password="$(openssl rand -hex 24)"
identity_db_password="$(openssl rand -hex 24)"
catalog_db_password="$(openssl rand -hex 24)"
keycloak_db_password="$(openssl rand -hex 24)"
keycloak_admin_password="$(openssl rand -hex 24)"
identity_oidc_client_secret="$(openssl rand -hex 32)"
identity_auth_encryption_key="$(openssl rand -base64 32)"
identity_auth_hmac_key="$(openssl rand -base64 32)"
fixture_customer_password="$(openssl rand -hex 24)"
fixture_non_maintainer_password="$(openssl rand -hex 24)"
fixture_maintainer_password="$(openssl rand -hex 24)"
fixture_lockout_password="$(openssl rand -hex 24)"

umask 077
touch "$env_file"
chmod 600 "$env_file"

ensure_value() {
  local name="$1"
  local value="$2"
  if ! grep -q "^${name}=" "$env_file"; then
    printf '%s=%s\n' "$name" "$value" >> "$env_file"
  fi
}

ensure_value POSTGRES_ADMIN_PASSWORD "$postgres_admin_password"
ensure_value IDENTITY_DB_USER identity_access_app
ensure_value IDENTITY_DB_PASSWORD "$identity_db_password"
ensure_value CATALOG_DB_USER catalog_app
ensure_value CATALOG_DB_PASSWORD "$catalog_db_password"
ensure_value KEYCLOAK_DB_USER keycloak_app
ensure_value KEYCLOAK_DB_PASSWORD "$keycloak_db_password"
ensure_value KEYCLOAK_ADMIN_USER local_admin
ensure_value KEYCLOAK_ADMIN_PASSWORD "$keycloak_admin_password"
ensure_value IDENTITY_OIDC_CLIENT_SECRET "$identity_oidc_client_secret"
ensure_value IDENTITY_AUTH_ENCRYPTION_KEY_ID local-aes-2026-01
ensure_value IDENTITY_AUTH_ENCRYPTION_KEY "$identity_auth_encryption_key"
ensure_value IDENTITY_AUTH_HMAC_KEY_ID local-hmac-2026-01
ensure_value IDENTITY_AUTH_HMAC_KEY "$identity_auth_hmac_key"
ensure_value IDENTITY_FIXTURE_CUSTOMER_PASSWORD "$fixture_customer_password"
ensure_value IDENTITY_FIXTURE_NON_MAINTAINER_PASSWORD "$fixture_non_maintainer_password"
ensure_value IDENTITY_FIXTURE_MAINTAINER_PASSWORD "$fixture_maintainer_password"
ensure_value IDENTITY_FIXTURE_LOCKOUT_PASSWORD "$fixture_lockout_password"

echo "Ensured secret-safe local overrides at .env (mode 600, git-ignored)."
