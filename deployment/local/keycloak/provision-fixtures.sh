#!/bin/sh
set -eu

KCADM=/opt/keycloak/bin/kcadm.sh
SERVER=http://keycloak:8080

"$KCADM" config credentials --server "$SERVER" --realm master --user "$KEYCLOAK_ADMIN_USER" --password "$KEYCLOAK_ADMIN_PASSWORD"

ensure_user() {
    username="$1"
    password="$2"
    role="${3:-}"

    if ! "$KCADM" get users -r commerce -q "username=$username" | grep -q '"id"'; then
        "$KCADM" create users -r commerce -s "username=$username" -s enabled=true -s emailVerified=true
    fi
    "$KCADM" set-password -r commerce --username "$username" --new-password "$password"
    if [ -n "$role" ]; then
        "$KCADM" add-roles -r commerce --uusername "$username" --rolename "$role"
    fi
}

ensure_user synthetic-customer "$IDENTITY_FIXTURE_CUSTOMER_PASSWORD" CUSTOMER
ensure_user synthetic-non-maintainer "$IDENTITY_FIXTURE_NON_MAINTAINER_PASSWORD"
ensure_user synthetic-maintainer "$IDENTITY_FIXTURE_MAINTAINER_PASSWORD" CATALOG_MAINTAINER
ensure_user synthetic-lockout "$IDENTITY_FIXTURE_LOCKOUT_PASSWORD"

echo "Keycloak private synthetic fixtures provisioned."
