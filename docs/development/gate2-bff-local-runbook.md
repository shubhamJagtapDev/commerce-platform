# Gate 2 local BFF authentication runbook

Gate 2 is deliberately an authentication-only boundary. It creates an opaque BFF session for a
synthetic `CUSTOMER` or `CATALOG_MAINTAINER`; it does not create or bind a customer account.
Existing Gate 2 sessions are never upgraded implicitly when Gate 4 introduces account binding.

## Local setup

Run `./dev start`. The idempotent `keycloak-fixtures` service creates four local-only principals
using passwords generated in the ignored `.env` file:

| Username | Purpose |
|---|---|
| `synthetic-customer` | accepted `CUSTOMER` BFF login |
| `synthetic-maintainer` | accepted `CATALOG_MAINTAINER` BFF login |
| `synthetic-non-maintainer` | authenticates but must receive no BFF session |
| `synthetic-lockout` | isolated temporary brute-force verification |

Do not copy passwords into tickets, browser recordings, logs, or evidence. To provision an existing
running realm again, use `docker compose --env-file .env -f deployment/local/compose.yaml up --no-deps keycloak-fixtures`.

The realm import is pinned by the realm JSON digest and the vendored, checksummed MIT-licensed
SecLists 2026.1 10k blacklist. `./dev verify` runs the non-mutating realm-drift check. Existing
Keycloak volumes are only reconciled deliberately with `scripts/reconcile-keycloak-realm.sh`, then
verified again; the reconcile script must not be used as a substitute for reviewing realm changes.

`identity-access-bff` has a client-scoped realm-role mapper that emits `realm_access.roles` only in
the ID token. Identity Access uses that signed claim to select exactly one Gate 2 actor kind before
it creates a BFF session. The built-in Keycloak `roles` scope remains responsible for access-token
roles, so this mapper must not be removed or widened to other clients.

## Browser acceptance checklist

1. Navigate to `http://localhost:8080/bff/login` and authenticate as the customer, then the maintainer.
2. Confirm the final callback redirects to `/bff/csrf`, which returns only `{ "token": "…" }` and
   `Cache-Control: no-store`.
3. In browser storage, confirm the only application credential is the HttpOnly `commerce-session`
   cookie. No bearer token, raw password, authorization code, state, nonce, or PKCE verifier may be
   visible in local storage, session storage, or application cookies.
4. In network inspection, confirm Keycloak uses the public `localhost:8082` authorization endpoint;
   browser requests never carry the token exchange. Confirm the non-maintainer ends with a generic
   failure and no `Set-Cookie` session.
5. Repeat a callback URL and confirm it is rejected without issuing a second cookie. Test a 15-character
   password, a 128-character password, a blacklist entry, and five failed lockout attempts only with
   the isolated lockout fixture.

## Cookie and local TLS boundary

The `dev` profile has a documented loopback-only exception: `commerce-session; HttpOnly; SameSite=Lax; Path=/`
without `Secure`. Staging and production start with `__Host-commerce-session; Secure; HttpOnly; SameSite=Lax; Path=/`
and no `Domain` attribute.

Before using a non-loopback environment, install a trusted local certificate, serve Identity Access and
Keycloak over HTTPS, update Keycloak's exact web origin and callback redirect URI to HTTPS, set
`IDENTITY_BFF_PUBLIC_ORIGIN` and `IDENTITY_OIDC_PUBLIC_ISSUER` to those HTTPS origins, and retain the
production cookie attributes. This gate does not automate local TLS.

Logout, refresh, back-channel logout, registration, customer-account endpoints, Catalog routing, and
Gateway token relay are intentionally absent from Gate 2.
