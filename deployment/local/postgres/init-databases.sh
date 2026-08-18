#!/usr/bin/env bash
set -euo pipefail

psql --username postgres --dbname postgres \
  --set=identity_password="$IDENTITY_DB_PASSWORD" \
  --set=catalog_password="$CATALOG_DB_PASSWORD" \
  --set=keycloak_password="$KEYCLOAK_DB_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE identity_access_app LOGIN PASSWORD %L', :'identity_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'identity_access_app') \gexec

SELECT format('CREATE ROLE catalog_app LOGIN PASSWORD %L', :'catalog_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'catalog_app') \gexec

SELECT format('CREATE ROLE keycloak_app LOGIN PASSWORD %L', :'keycloak_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'keycloak_app') \gexec

SELECT 'CREATE DATABASE identity_access OWNER identity_access_app'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'identity_access') \gexec

SELECT 'CREATE DATABASE catalog OWNER catalog_app'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'catalog') \gexec

SELECT 'CREATE DATABASE keycloak OWNER keycloak_app'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloak') \gexec

REVOKE CONNECT ON DATABASE identity_access FROM PUBLIC;
REVOKE CONNECT ON DATABASE catalog FROM PUBLIC;
REVOKE CONNECT ON DATABASE keycloak FROM PUBLIC;
GRANT CONNECT ON DATABASE identity_access TO identity_access_app;
GRANT CONNECT ON DATABASE catalog TO catalog_app;
GRANT CONNECT ON DATABASE keycloak TO keycloak_app;
SQL
