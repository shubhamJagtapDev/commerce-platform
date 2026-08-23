#!/usr/bin/env bash
set -Eeuo pipefail

workspace_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$workspace_dir"

source .env
temporary_dir="$(mktemp -d)"
verification_step="initializing"
admin_token=""
mixed_role_user_id=""
mixed_roles_payload=""

cleanup() {
  if [[ -n "$admin_token" && -n "$mixed_role_user_id" && -n "$mixed_roles_payload" ]]; then
    curl --silent --show-error --max-time 5 \
      --request DELETE \
      --header "Authorization: Bearer $admin_token" \
      --header 'Content-Type: application/json' \
      --data "$mixed_roles_payload" \
      --output /dev/null \
      "http://localhost:8082/admin/realms/commerce/users/$mixed_role_user_id/role-mappings/realm" || true
  fi
  rm -rf "$temporary_dir"
}

trap cleanup EXIT
trap 'printf "BFF OIDC verification failed during %s.\n" "$verification_step" >&2' ERR

assign_mixed_actor_roles() {
  local customer_role
  local maintainer_role

  verification_step="mixed-role: obtain temporary Keycloak administration grant"
  admin_token="$(curl --fail --silent --show-error --max-time 5 \
    --data-urlencode 'client_id=admin-cli' \
    --data-urlencode 'grant_type=password' \
    --data-urlencode "username=$KEYCLOAK_ADMIN_USER" \
    --data-urlencode "password=$KEYCLOAK_ADMIN_PASSWORD" \
    http://localhost:8082/realms/master/protocol/openid-connect/token | jq -er '.access_token')"
  verification_step="mixed-role: resolve isolated fixture and actor roles"
  mixed_role_user_id="$(curl --fail --silent --show-error --max-time 5 \
    --header "Authorization: Bearer $admin_token" \
    'http://localhost:8082/admin/realms/commerce/users?username=synthetic-non-maintainer&exact=true' \
    | jq -er 'if length == 1 then .[0].id else error("isolated fixture is missing or duplicated") end')"
  customer_role="$(curl --fail --silent --show-error --max-time 5 \
    --header "Authorization: Bearer $admin_token" \
    http://localhost:8082/admin/realms/commerce/roles/CUSTOMER)"
  maintainer_role="$(curl --fail --silent --show-error --max-time 5 \
    --header "Authorization: Bearer $admin_token" \
    http://localhost:8082/admin/realms/commerce/roles/CATALOG_MAINTAINER)"
  mixed_roles_payload="$(jq -cn --argjson customer "$customer_role" --argjson maintainer "$maintainer_role" \
    '[$customer, $maintainer]')"
  verification_step="mixed-role: temporarily assign both actor roles"
  curl --fail --silent --show-error --max-time 5 \
    --request POST \
    --header "Authorization: Bearer $admin_token" \
    --header 'Content-Type: application/json' \
    --data "$mixed_roles_payload" \
    --output /dev/null \
    "http://localhost:8082/admin/realms/commerce/users/$mixed_role_user_id/role-mappings/realm"
}

remove_mixed_actor_roles() {
  verification_step="mixed-role: restore isolated fixture roles"
  curl --fail --silent --show-error --max-time 5 \
    --request DELETE \
    --header "Authorization: Bearer $admin_token" \
    --header 'Content-Type: application/json' \
    --data "$mixed_roles_payload" \
    --output /dev/null \
    "http://localhost:8082/admin/realms/commerce/users/$mixed_role_user_id/role-mappings/realm"
  mixed_roles_payload=""
}

login_action_for() {
  local authorization_url="$1"
  local cookie_jar="$2"
  local login_page="$3"

  verification_step="load Keycloak login form"
  curl --fail --silent --show-error --max-time 5 \
    --cookie "$cookie_jar" \
    --cookie-jar "$cookie_jar" \
    --output "$login_page" \
    "$authorization_url"
  verification_step="extract Keycloak login form action"
  perl -0777 -ne '
    if (/<form\s+id="kc-form-login"[^>]*\saction="([^"]+)"/s) {
      $action = $1;
      $action =~ s/&amp;/&/g;
      print $action;
    }
  ' "$login_page"
}

start_login() {
  local name="$1"
  local response_headers="$temporary_dir/$name-start.headers"
  local authorization_url

  verification_step="$name: start BFF login"
  curl --fail --silent --show-error --max-time 5 \
    --dump-header "$response_headers" \
    --output /dev/null \
    http://localhost:8080/bff/login
  authorization_url="$(awk 'tolower($1) == "location:" { print $2 }' "$response_headers" | tr -d '\r')"
  verification_step="$name: validate Keycloak authorization redirect"
  [[ "$authorization_url" == http://localhost:8082/realms/commerce/protocol/openid-connect/auth\?* ]]
  printf '%s' "$authorization_url"
}

url_encode() {
  jq -rn --arg value "$1" '$value | @uri'
}

verify_id_token_claim_contract() {
  local name="$1"
  local username="$2"
  local password="$3"
  local expected_role="$4"
  local verifier
  local nonce
  local state
  local challenge
  local authorization_url
  local cookie_jar="$temporary_dir/$name-id-token.cookies"
  local login_action
  local callback_headers="$temporary_dir/$name-id-token-callback.headers"
  local callback_url
  local callback_destination
  local authorization_code
  local token_response="$temporary_dir/$name-id-token-response.json"
  local token_payload="$temporary_dir/$name-id-token-payload.json"
  local access_token_payload="$temporary_dir/$name-access-token-payload.json"
  local encoded_payload
  local encoded_access_payload
  local remainder
  local token_status

  verification_step="$name: create PKCE ID-token contract request"
  verifier="$(openssl rand -hex 64)"
  nonce="$(openssl rand -hex 32)"
  state="$(openssl rand -hex 32)"
  challenge="$(printf '%s' "$verifier" | openssl dgst -binary -sha256 | openssl base64 -A | tr '+/' '-_' | tr -d '=')"
  authorization_url="http://localhost:8082/realms/commerce/protocol/openid-connect/auth?client_id=identity-access-bff&response_type=code&scope=openid%20roles&redirect_uri=$(url_encode 'http://localhost:8080/login/oauth2/code/keycloak')&state=$(url_encode "$state")&nonce=$(url_encode "$nonce")&code_challenge=$(url_encode "$challenge")&code_challenge_method=S256"
  login_action="$(login_action_for "$authorization_url" "$cookie_jar" "$temporary_dir/$name-id-token-login.html")"
  verification_step="$name: submit ID-token contract credentials"
  curl --fail --silent --show-error --max-time 5 \
    --cookie "$cookie_jar" \
    --cookie-jar "$cookie_jar" \
    --dump-header "$callback_headers" \
    --output /dev/null \
    --data-urlencode "username=$username" \
    --data-urlencode "password=$password" \
    --data-urlencode 'credentialId=' \
    "$login_action"
  verification_step="$name: read ID-token contract callback"
  callback_url="$(awk 'tolower($1) == "location:" { print $2 }' "$callback_headers" | tr -d '\r')"
  callback_destination="$(printf '%s' "$callback_url" | sed 's/?.*$//')"
  if [[ "$callback_destination" != "http://localhost:8080/login/oauth2/code/keycloak" ]]; then
    printf 'Unexpected ID-token contract callback destination: %s\n' "$callback_destination" >&2
    false
  fi
  authorization_code="$(sed -n 's/.*[?&]code=\([^&]*\).*/\1/p' <<<"$callback_url")"
  [[ -n "$authorization_code" ]]
  verification_step="$name: exchange ID-token contract code"
  token_status="$(curl --silent --show-error --max-time 5 \
    --data-urlencode 'grant_type=authorization_code' \
    --data-urlencode 'client_id=identity-access-bff' \
    --user "identity-access-bff:$IDENTITY_OIDC_CLIENT_SECRET" \
    --data-urlencode "code=$authorization_code" \
    --data-urlencode 'redirect_uri=http://localhost:8080/login/oauth2/code/keycloak' \
    --data-urlencode "code_verifier=$verifier" \
    --output "$token_response" \
    --write-out '%{http_code}' \
    http://localhost:8082/realms/commerce/protocol/openid-connect/token)"
  if [[ "$token_status" != "200" ]]; then
    printf 'ID-token contract exchange failed with Keycloak error: %s\n' \
      "$(jq -r '.error // "unknown"' "$token_response")" >&2
    false
  fi
  verification_step="$name: decode ID-token claim contract"
  encoded_payload="$(jq -er '.id_token | split(".")[1]' "$token_response")"
  encoded_payload="${encoded_payload//-/+}"
  encoded_payload="${encoded_payload//_/\/}"
  remainder=$((${#encoded_payload} % 4))
  if [[ "$remainder" -eq 2 ]]; then
    encoded_payload+="=="
  elif [[ "$remainder" -eq 3 ]]; then
    encoded_payload+="="
  elif [[ "$remainder" -ne 0 ]]; then
    false
  fi
  printf '%s' "$encoded_payload" | openssl base64 -d -A > "$token_payload"
  encoded_access_payload="$(jq -er '.access_token | split(".")[1]' "$token_response")"
  encoded_access_payload="${encoded_access_payload//-/+}"
  encoded_access_payload="${encoded_access_payload//_/\/}"
  remainder=$((${#encoded_access_payload} % 4))
  if [[ "$remainder" -eq 2 ]]; then
    encoded_access_payload+="=="
  elif [[ "$remainder" -eq 3 ]]; then
    encoded_access_payload+="="
  elif [[ "$remainder" -ne 0 ]]; then
    false
  fi
  printf '%s' "$encoded_access_payload" | openssl base64 -d -A > "$access_token_payload"
  verification_step="$name: validate ID-token claim contract"
  jq -e \
    --arg issuer 'http://localhost:8082/realms/commerce' \
    --arg client_id 'identity-access-bff' \
    --arg nonce "$nonce" \
    --arg expected_role "$expected_role" '
      .iss == $issuer
      and (.aud | if type == "array" then index($client_id) != null else . == $client_id end)
      and .azp == $client_id
      and .nonce == $nonce
      and (.realm_access.roles | index($expected_role) != null)
    ' "$token_payload" >/dev/null
  verification_step="$name: validate access-token role scope contract"
  jq -e --arg expected_role "$expected_role" \
    '.realm_access.roles | index($expected_role) != null' "$access_token_payload" >/dev/null
}

verify_accepted_login() {
  local name="$1"
  local username="$2"
  local password="$3"
  local cookie_jar="$temporary_dir/$name.cookies"
  local login_action
  local callback_headers="$temporary_dir/$name-callback.headers"
  local csrf_response="$temporary_dir/$name-csrf.json"
  local callback_url

  verification_step="$name: initiate Keycloak login"
  login_action="$(login_action_for "$(start_login "$name")" "$cookie_jar" "$temporary_dir/$name-login.html")"
  verification_step="$name: validate Keycloak login action"
  [[ "$login_action" == http://localhost:8082/realms/commerce/login-actions/authenticate\?* ]]
  verification_step="$name: submit credentials"
  curl --fail --silent --show-error --max-time 5 \
    --cookie "$cookie_jar" \
    --cookie-jar "$cookie_jar" \
    --dump-header "$callback_headers" \
    --output /dev/null \
    --data-urlencode "username=$username" \
    --data-urlencode "password=$password" \
    --data-urlencode 'credentialId=' \
    "$login_action"
  verification_step="$name: read authorization callback redirect"
  callback_url="$(awk 'tolower($1) == "location:" { print $2 }' "$callback_headers" | tr -d '\r')"
  verification_step="$name: validate authorization callback redirect"
  [[ "$callback_url" == http://localhost:8080/login/oauth2/code/keycloak\?* ]]
  verification_step="$name: complete Identity Access callback"
  [[ "$(curl --silent --show-error --max-time 5 \
    --cookie "$cookie_jar" \
    --cookie-jar "$cookie_jar" \
    --dump-header "$callback_headers" \
    --output /dev/null \
    --write-out '%{http_code}' \
    "$callback_url")" == "302" ]]
  verification_step="$name: validate BFF success redirect"
  local success_redirect
  success_redirect="$(awk 'tolower($1) == "location:" { print $2 }' "$callback_headers" | tr -d '\r' | sed 's/?.*$//')"
  if [[ "$success_redirect" != "/bff/csrf" && "$success_redirect" != "http://localhost:8080/bff/csrf" ]]; then
    printf 'Unexpected BFF success redirect destination: %s\n' "$success_redirect" >&2
    grep '^HTTP/' "$callback_headers" >&2
    awk -F ':' 'NF > 1 { print tolower($1) }' "$callback_headers" | sort -u | tr '\n' ' ' >&2
    printf '\n' >&2
    false
  fi
  verification_step="$name: validate opaque session cookie"
  grep -qi '^set-cookie: commerce-session=' "$callback_headers"
  verification_step="$name: retrieve CSRF token"
  curl --fail --silent --show-error --max-time 5 \
    --cookie "$cookie_jar" \
    --output "$csrf_response" \
    http://localhost:8080/bff/csrf
  verification_step="$name: validate CSRF response"
  jq -e 'keys == ["token"] and (.token | type == "string" and length > 0)' "$csrf_response" >/dev/null
}

verify_rejected_login() {
  local name="$1"
  local username="$2"
  local password="$3"
  local cookie_jar="$temporary_dir/$name.cookies"
  local login_action
  local callback_headers="$temporary_dir/$name-callback.headers"
  local callback_response="$temporary_dir/$name-callback.json"
  local callback_url

  verification_step="$name: initiate Keycloak login"
  login_action="$(login_action_for "$(start_login "$name")" "$cookie_jar" "$temporary_dir/$name-login.html")"
  verification_step="$name: validate Keycloak login action"
  curl --fail --silent --show-error --max-time 5 \
    --cookie "$cookie_jar" \
    --cookie-jar "$cookie_jar" \
    --dump-header "$callback_headers" \
    --output /dev/null \
    --data-urlencode "username=$username" \
    --data-urlencode "password=$password" \
    --data-urlencode 'credentialId=' \
    "$login_action"
  verification_step="$name: read authorization callback redirect"
  callback_url="$(awk 'tolower($1) == "location:" { print $2 }' "$callback_headers" | tr -d '\r')"
  verification_step="$name: validate authorization callback redirect"
  [[ "$callback_url" == http://localhost:8080/login/oauth2/code/keycloak\?* ]]
  verification_step="$name: complete rejected callback"
  [[ "$(curl --silent --show-error --max-time 5 \
    --cookie "$cookie_jar" \
    --cookie-jar "$cookie_jar" \
    --dump-header "$callback_headers" \
    --output "$callback_response" \
    --write-out '%{http_code}' \
    "$callback_url")" == "401" ]]
  verification_step="$name: validate safe rejection response"
  jq -e '
    .type == "urn:commerce:problem:authentication-failed"
    and .title == "Authentication failed"
    and .status == 401
  ' "$callback_response" >/dev/null
  verification_step="$name: confirm rejected login has no BFF session"
  ! grep -qi '^set-cookie: commerce-session=' "$callback_headers"
}

verify_id_token_claim_contract customer synthetic-customer "$IDENTITY_FIXTURE_CUSTOMER_PASSWORD" CUSTOMER
verify_id_token_claim_contract maintainer synthetic-maintainer "$IDENTITY_FIXTURE_MAINTAINER_PASSWORD" CATALOG_MAINTAINER
verify_accepted_login customer synthetic-customer "$IDENTITY_FIXTURE_CUSTOMER_PASSWORD"
verify_accepted_login maintainer synthetic-maintainer "$IDENTITY_FIXTURE_MAINTAINER_PASSWORD"
verify_rejected_login non-maintainer synthetic-non-maintainer "$IDENTITY_FIXTURE_NON_MAINTAINER_PASSWORD"
assign_mixed_actor_roles
verify_rejected_login mixed-role synthetic-non-maintainer "$IDENTITY_FIXTURE_NON_MAINTAINER_PASSWORD"
remove_mixed_actor_roles

echo "BFF OIDC role-boundary flow: PASS"
