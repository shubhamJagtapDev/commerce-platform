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
        "$KCADM" create users -r commerce \
            -s "username=$username" \
            -s "email=$username@fixtures.local" \
            -s firstName=Synthetic \
            -s lastName=Fixture \
            -s enabled=true \
            -s emailVerified=true
    fi
    user_id="$("$KCADM" get users -r commerce -q "username=$username" | sed -n 's/.*"id" : "\([^"]*\)".*/\1/p' | head -n 1)"
    user_profile="$("$KCADM" get "users/$user_id" -r commerce)"
    if ! grep -q '"email"' <<<"$user_profile"; then
        "$KCADM" update "users/$user_id" -r commerce \
            -s "email=$username@fixtures.local" \
            -s emailVerified=true
    fi
    if ! grep -q '"firstName"' <<<"$user_profile"; then
        "$KCADM" update "users/$user_id" -r commerce -s firstName=Synthetic
    fi
    if ! grep -q '"lastName"' <<<"$user_profile"; then
        "$KCADM" update "users/$user_id" -r commerce -s lastName=Fixture
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
