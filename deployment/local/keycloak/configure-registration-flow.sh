#!/bin/sh
set -eu

KCADM=/opt/keycloak/bin/kcadm.sh
SERVER="${KEYCLOAK_CONFIGURATION_SERVER:-http://keycloak:8080}"
FLOW_ALIAS=commerce-registration
FORM_ALIAS=commerce-registration%20registration%20form
PROVIDER_ID=commerce-registration-intent
admin_user="${KEYCLOAK_ADMIN_USER:-${KC_BOOTSTRAP_ADMIN_USERNAME:?Keycloak admin user is required}}"
admin_password="${KEYCLOAK_ADMIN_PASSWORD:-${KC_BOOTSTRAP_ADMIN_PASSWORD:?Keycloak admin password is required}}"

"$KCADM" config credentials \
    --server "$SERVER" \
    --realm master \
    --user "$admin_user" \
    --password "$admin_password" >/dev/null

"$KCADM" remove-roles \
    -r commerce \
    --rname default-roles-commerce \
    --rolename CUSTOMER >/dev/null 2>&1 || true

if ! "$KCADM" get authentication/flows -r commerce --fields alias --format csv --noquotes \
    | grep -qx "$FLOW_ALIAS"; then
    "$KCADM" create authentication/flows/registration/copy \
        -r commerce \
        -s "newName=$FLOW_ALIAS" >/dev/null
fi

executions="$($KCADM get "authentication/flows/$FLOW_ALIAS/executions" \
    -r commerce \
    --fields id,providerId,requirement \
    --format csv \
    --noquotes)"
provider_line="$(printf '%s\n' "$executions" | grep ",$PROVIDER_ID," || true)"
if [ -z "$provider_line" ]; then
    "$KCADM" create "authentication/flows/$FORM_ALIAS/executions/execution" \
        -r commerce \
        -s "provider=$PROVIDER_ID" >/dev/null
    executions="$($KCADM get "authentication/flows/$FLOW_ALIAS/executions" \
        -r commerce \
        --fields id,providerId,requirement \
        --format csv \
        --noquotes)"
    provider_line="$(printf '%s\n' "$executions" | grep ",$PROVIDER_ID,")"
fi

provider_id="${provider_line%%,*}"
"$KCADM" update "authentication/flows/$FLOW_ALIAS/executions" \
    -r commerce \
    -n \
    -s "id=$provider_id" \
    -s requirement=REQUIRED \
    -s priority=30
"$KCADM" update realms/commerce -s "registrationFlow=$FLOW_ALIAS"

echo "Guarded Keycloak registration flow configured."
