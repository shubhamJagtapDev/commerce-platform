#!/usr/bin/env bash
set -euo pipefail

workspace_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$workspace_dir"

source .env

admin_token="$({
  curl --fail --silent --show-error --max-time 3 \
    --data-urlencode 'client_id=admin-cli' \
    --data-urlencode 'grant_type=password' \
    --data-urlencode "username=$KEYCLOAK_ADMIN_USER" \
    --data-urlencode "password=$KEYCLOAK_ADMIN_PASSWORD" \
    http://localhost:8082/realms/master/protocol/openid-connect/token
} | jq -r '.access_token')"

[[ -n "$admin_token" && "$admin_token" != "null" ]]

realm="$(curl --fail --silent --show-error --max-time 3 \
  -H "Authorization: Bearer $admin_token" \
  http://localhost:8082/admin/realms/commerce)"
client="$(curl --fail --silent --show-error --max-time 3 \
  -H "Authorization: Bearer $admin_token" \
  'http://localhost:8082/admin/realms/commerce/clients?clientId=identity-access-bff' \
  | jq -e 'if length == 1 then .[0] else error("identity-access-bff client is missing or duplicated") end')"
role_mappers="$(curl --fail --silent --show-error --max-time 3 \
  -H "Authorization: Bearer $admin_token" \
  "http://localhost:8082/admin/realms/commerce/clients/$(jq -r '.id' <<<"$client")/protocol-mappers/models")"

jq -e '
  .registrationAllowed == false
  and .rememberMe == false
  and .bruteForceProtected == true
  and .permanentLockout == false
  and .failureFactor == 5
  and .waitIncrementSeconds == 30
  and .maxFailureWaitSeconds == 900
  and .maxDeltaTimeSeconds == 43200
  and (.passwordPolicy | contains("hashAlgorithm(argon2)"))
  and (.passwordPolicy | contains("length(15)"))
  and (.passwordPolicy | contains("passwordBlacklist(10k-most-common.txt)"))
' <<<"$realm" >/dev/null

jq -e '
  .publicClient == false
  and .standardFlowEnabled == true
  and .implicitFlowEnabled == false
  and .directAccessGrantsEnabled == false
  and .serviceAccountsEnabled == false
  and .redirectUris == ["http://localhost:8080/login/oauth2/code/keycloak"]
  and .webOrigins == ["http://localhost:8080"]
  and (.defaultClientScopes | index("roles") != null)
  and .attributes["pkce.code.challenge.method"] == "S256"
  and .attributes["post.logout.redirect.uris"] == "http://localhost:8080/"
  and .attributes["oauth2.device.authorization.grant.enabled"] == "false"
' <<<"$client" >/dev/null

jq -e '
  [ .[] | select(.name == "bff-id-token-realm-roles") ] as $mappers
  | ($mappers | length) == 1
    and $mappers[0].protocol == "openid-connect"
    and $mappers[0].protocolMapper == "oidc-usermodel-realm-role-mapper"
    and $mappers[0].consentRequired == false
    and $mappers[0].config == {
      "claim.name": "realm_access.roles",
      "jsonType.label": "String",
      "multivalued": "true",
      "access.token.claim": "false",
      "id.token.claim": "true",
      "userinfo.token.claim": "false",
      "introspection.token.claim": "false"
    }
' <<<"$role_mappers" >/dev/null

jq -e '
  [ .[] | select(.name == "catalog-access-token-subject") ] as $mappers
  | ($mappers | length) == 1
    and $mappers[0].protocol == "openid-connect"
    and $mappers[0].protocolMapper == "oidc-sub-mapper"
    and $mappers[0].consentRequired == false
    and $mappers[0].config == {
      "access.token.claim": "true",
      "lightweight.claim": "true",
      "introspection.token.claim": "true"
    }
' <<<"$role_mappers" >/dev/null

jq -e '
  [ .[] | select(.name == "catalog-api-audience") ] as $mappers
  | ($mappers | length) == 1
    and $mappers[0].protocol == "openid-connect"
    and $mappers[0].protocolMapper == "oidc-audience-mapper"
    and $mappers[0].consentRequired == false
    and $mappers[0].config == {
      "included.client.audience": "catalog-api",
      "access.token.claim": "true",
      "id.token.claim": "false",
      "introspection.token.claim": "true",
      "userinfo.token.claim": "false"
    }
' <<<"$role_mappers" >/dev/null

echo "Keycloak realm drift check: PASS"
