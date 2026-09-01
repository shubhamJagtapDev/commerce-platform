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
    "registrationAllowed": true,
    "rememberMe": false,
    "bruteForceProtected": true,
    "permanentLockout": false,
    "failureFactor": 5,
    "waitIncrementSeconds": 30,
    "maxFailureWaitSeconds": 900,
    "maxDeltaTimeSeconds": 43200,
    "quickLoginCheckMilliSeconds": 1000,
    "minimumQuickLoginWaitSeconds": 30,
    "passwordPolicy": "hashAlgorithm(argon2) and length(15) and maxLength(128) and passwordBlacklist(10k-most-common.txt)"
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

reconcile_mapper() {
  local mapper_name="$1"
  local mapper_payload
  local mapper_id

  mapper_payload="$(jq -c --arg mapper_name "$mapper_name" '
  .clients[]
  | select(.clientId == "identity-access-bff")
  | .protocolMappers[]
  | select(.name == $mapper_name)
' deployment/local/keycloak/commerce-realm.json)"
  mapper_id="$(curl --fail --silent --show-error --max-time 3 \
  -H "Authorization: Bearer $admin_token" \
  "http://localhost:8082/admin/realms/commerce/clients/$client_id/protocol-mappers/models" \
  | jq -r --arg mapper_name "$mapper_name" '
      [ .[] | select(.name == $mapper_name) ]
      | if length == 0 then ""
        elif length == 1 then .[0].id
        else error("protocol mapper is duplicated: \($mapper_name)")
        end')"

  if [[ -z "$mapper_id" ]]; then
    curl --fail --silent --show-error --max-time 3 \
      -X POST \
      -H "Authorization: Bearer $admin_token" \
      -H 'Content-Type: application/json' \
      --data "$mapper_payload" \
      "http://localhost:8082/admin/realms/commerce/clients/$client_id/protocol-mappers/models"
  else
    curl --fail --silent --show-error --max-time 3 \
      -X PUT \
      -H "Authorization: Bearer $admin_token" \
      -H 'Content-Type: application/json' \
      --data "$(jq -c --arg id "$mapper_id" '. + {id: $id}' <<<"$mapper_payload")" \
      "http://localhost:8082/admin/realms/commerce/clients/$client_id/protocol-mappers/models/$mapper_id"
  fi
}

reconcile_mapper "catalog-access-token-subject"
reconcile_mapper "bff-id-token-realm-roles"
reconcile_mapper "catalog-api-audience"

docker compose --env-file .env -f deployment/local/compose.yaml exec -T \
  -e KEYCLOAK_CONFIGURATION_SERVER=http://localhost:8080 \
  keycloak \
  /opt/keycloak/configuration/configure-registration-flow.sh

echo "Keycloak realm reconciled. Re-run ./dev verify before using the local stack."
