#!/usr/bin/env bash
set -euo pipefail

workspace_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$workspace_dir"

[[ -f .env ]] || scripts/init-local-env.sh
source .env

admin_token="$(curl --fail --silent --show-error --max-time 3 \
  --data-urlencode 'client_id=admin-cli' \
  --data-urlencode 'grant_type=password' \
  --data-urlencode "username=$KEYCLOAK_ADMIN_USER" \
  --data-urlencode "password=$KEYCLOAK_ADMIN_PASSWORD" \
  http://localhost:8082/realms/master/protocol/openid-connect/token | jq -er '.access_token')"

curl --fail --silent --show-error --max-time 3 \
  -X PUT \
  -H "Authorization: Bearer $admin_token" \
  -H 'Content-Type: application/json' \
  --data '{
    "registrationAllowed": false,
    "rememberMe": false,
    "bruteForceProtected": true,
    "permanentLockout": false,
    "failureFactor": 5,
    "waitIncrementSeconds": 30,
    "maxFailureWaitSeconds": 900,
    "maxDeltaTimeSeconds": 43200,
    "quickLoginCheckMilliSeconds": 1000,
    "minimumQuickLoginWaitSeconds": 30,
    "passwordPolicy": "hashAlgorithm(argon2) and length(15) and maxLength(128) and passwordBlacklist(10000)"
  }' \
  http://localhost:8082/admin/realms/commerce

client_id="$(curl --fail --silent --show-error --max-time 3 \
  -H "Authorization: Bearer $admin_token" \
  'http://localhost:8082/admin/realms/commerce/clients?clientId=identity-access-bff' \
  | jq -er 'if length == 1 then .[0].id else error("identity-access-bff client is missing or duplicated") end')"
client_payload="$(jq -c '
  .clients[] | select(.clientId == "identity-access-bff") |
  del(.id, .secret) |
  .attributes = {
    "pkce.code.challenge.method": "S256",
    "post.logout.redirect.uris": "http://localhost:8080/",
    "oauth2.device.authorization.grant.enabled": "false"
  }
' deployment/local/keycloak/commerce-realm.json)"

curl --fail --silent --show-error --max-time 3 \
  -X PUT \
  -H "Authorization: Bearer $admin_token" \
  -H 'Content-Type: application/json' \
  --data "$client_payload" \
  "http://localhost:8082/admin/realms/commerce/clients/$client_id"

echo "Keycloak realm reconciled. Re-run ./dev verify before using the local stack."
