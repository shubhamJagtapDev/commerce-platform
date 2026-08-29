# Commerce Platform Postman workspace

This folder contains a contract-driven Postman collection for the local Identity Access and Catalog services.

## Setup

1. Start the stack with `./dev start`.
2. Import `environments/Commerce Platform - Local.environment.yaml` and select it.
3. Sync/import the `Commerce Platform API` collection from `postman/collections/Commerce Platform API`.
4. Copy these values from the local `.env` into the environment's current values. Do not commit them:
   - `IDENTITY_OIDC_CLIENT_SECRET` → `identityClientSecret`
   - `IDENTITY_FIXTURE_MAINTAINER_PASSWORD` → `fixtureMaintainerPassword`
   - `IDENTITY_FIXTURE_NON_MAINTAINER_PASSWORD` → `fixtureNonMaintainerPassword`
   - `IDENTITY_FIXTURE_CUSTOMER_PASSWORD` → `fixtureCustomerPassword`
   - `IDENTITY_FIXTURE_MAINTAINER_PASSWORD` → `bffPassword`

The collection intentionally contains blank secret variables. Postman will not have credentials until the local environment is filled in.

## OIDC Authorization Code + PKCE

The `03 - Catalog API (direct bearer token)` folder uses Postman's OAuth 2.0 helper. Open any request in that folder, choose `Authorization > OAuth 2.0 > Get New Access Token`, and use:

| Setting | Value |
| --- | --- |
| Grant Type | Authorization Code |
| Callback URL | `https://oauth.pstmn.io/v1/callback` |
| Auth URL | `{{keycloakBaseUrl}}/realms/{{realm}}/protocol/openid-connect/auth` |
| Access Token URL | `{{keycloakBaseUrl}}/realms/{{realm}}/protocol/openid-connect/token` |
| Client ID | `{{oidcClientId}}` |
| Client Secret | `{{identityClientSecret}}` |
| Scope | `openid roles` |
| Client Authentication | Send as Basic Auth header |
| PKCE | Enabled, method `S256` |

Sign in as `synthetic-maintainer` to get a token that can pass the catalog authorization probe. Sign in as `synthetic-non-maintainer` to verify the 403 authorization boundary. The resource server checks issuer, subject, `aud=catalog-api`, and `azp=identity-access-bff`; a successful request is evidence that these claims are present and valid.

Keep this OAuth 2.0 configuration at the direct Catalog folder level. Do not apply it to the whole collection: the BFF flow needs the opaque `commerce-session` cookie, not a bearer token.

The local Keycloak realm allowlists Postman's OAuth callback URLs only for local development. If the realm was already running before this collection change, run `scripts/reconcile-keycloak-realm.sh` or recreate the local stack before using the direct bearer-token folder.

## BFF session and CSRF flow

The `04 - BFF session and gateway authorization` folder is separate from the direct bearer flow. A bearer token does not create the opaque `commerce-session` cookie used by the BFF.

1. Enable Postman's cookie jar for `localhost`.
2. Set `bffUsername` and `bffPassword` to a local Keycloak fixture. The default username is `synthetic-maintainer`.
3. Run these requests in order: `BFF - start login (inspect redirect)`, `BFF - load Keycloak login page`, `BFF - submit Keycloak credentials`, `BFF - complete BFF callback`, `BFF - fetch CSRF token after login`, and finally `BFF - catalog authorization probe`.
4. The collection extracts the Keycloak form action, submits the credentials inside Postman, consumes the callback with the same cookie jar, and receives the opaque `commerce-session` cookie. No popup or browser cookie copy is required.
5. `BFF - fetch CSRF token after login` stores the response token in the environment's `csrfToken` value. The gateway request then sends the session cookie, `X-CSRF-TOKEN`, same-origin `Origin`, and the contract's idempotency key.

The collection's BFF tests accept the contract's meaningful outcomes (201/200, 401, or 403) and validate the exact Problem `status` and `code` for error responses. Use the request descriptions to select the expected scenario.

## Contract coverage

The collection covers every operation in `contracts/openapi/identity-access-v1.yaml` and `contracts/openapi/catalog-v1.yaml`: liveness/readiness, OIDC discovery and login redirect, CSRF retrieval, direct Catalog bearer authorization, BFF gateway authorization, idempotency replay shape, and the documented Problem responses.
