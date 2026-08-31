# Gate 4 and Gate 5 code-flow guide

> Scope: COM-45/T7A and COM-48/T11A. COM-44/T7B black-box evidence is deferred.
> Purpose: explain the implemented customer registration, account binding, and reusable ownership controls.
> Non-goal: this slice does not add a production customer profile, address, cart, or deletion API.

## End result

Gate 4 adds a bounded Keycloak-hosted registration entry point and binds every accepted customer to exactly one local account using the signed OIDC `(issuer, subject)` pair. Gate 5 adds a reusable conversion from an authenticated BFF session to an `ActiveCustomerPrincipal`. Future private-data repositories must query with that principal's `accountId`; callers cannot select their authority with an account id, email, role, issuer, subject, or forwarded header.

```mermaid
flowchart LR
    Browser[Browser] -->|GET /bff/register or /bff/login| BFF[Identity Access BFF]
    BFF -->|Authorization Code + PKCE| KC[Keycloak]
    KC -->|signed callback| BFF
    BFF -->|exact issuer + subject| Account[(customer_account)]
    BFF -->|opaque cookie only| Browser
    Browser -->|cookie| Filter[BFF session filter]
    Filter --> Session[(bff_session)]
    Filter -->|account id + issuer + subject + epoch| Account
    Account -->|ACTIVE exact match| Principal[ActiveCustomerPrincipal]
    Principal -->|future owner-scoped query| PrivateData[(customer-owned data)]
```

## Responsibility map

| Responsibility | Code location |
| --- | --- |
| Start login or bounded registration | `auth/controllers/BffAuthenticationController` |
| Build fixed login/registration OIDC requests | `auth/services/OidcAuthorizationRequestFactory` |
| Persist and claim one-time flow kind, nonce, and PKCE material | `auth/repositories/AuthTransactionStore` |
| Validate signed OIDC identity and exact actor role | `auth/services/OidcPrincipalValidator` |
| Atomically bind account and create session | `auth/services/BffSessionService` |
| Create/find exact principal binding | `customeraccount/repositories/CustomerAccountRepository` |
| Enforce active account and epoch | `customeraccount/services/CustomerAccountService` |
| Produce reusable ownership authority | `customeraccount/services/PrincipalAccessService` |
| Emit fixed-value, PII-free security audit events | `customeraccount/utils/CustomerAccountAuditLogger` |
| Return stable, non-enumerating denial problems | `config/GlobalProblemHandler` and Spring Security handlers |
| Enforce schema invariants and invalidate legacy customer sessions | `V003__create_customer_account_binding.sql` |

The `customeraccount` capability keeps models, repositories, services, and exceptions in their own responsibility packages. It intentionally has no controller or DTO yet because this slice exposes no customer-account resource API.

## Gate 4: registration and account binding

```mermaid
sequenceDiagram
    autonumber
    actor Browser
    participant Controller as BffAuthenticationController
    participant Limiter as RegistrationRateLimiter
    participant Tx as auth_transaction
    participant Keycloak
    participant Success as OIDC success handler
    participant Accounts as CustomerAccountService
    participant DB as identity_access DB

    Browser->>Controller: GET /bff/register
    Controller->>Controller: require registration.enabled
    Controller->>Limiter: check(direct peer address)
    alt disabled
        Controller-->>Browser: 404 NOT_FOUND
    else sixth start in one hour
        Limiter-->>Browser: 429 + Retry-After
    else accepted
        Controller->>Tx: store CUSTOMER_REGISTRATION + state/nonce/PKCE
        Controller-->>Browser: 302 Keycloak prompt=create
        Browser->>Keycloak: hosted registration and authentication
        Keycloak-->>Success: authorization callback
        Success->>Tx: atomically claim transaction
        Success->>Success: validate token, nonce, issuer, audience, exact CUSTOMER role
        Success->>Accounts: establish(issuer, subject)
        Accounts->>DB: INSERT ... ON CONFLICT DO NOTHING
        Accounts->>DB: SELECT by exact issuer + subject
        Success->>DB: insert session with accountId + securityEpoch
        DB-->>Success: commit account and session together
        Success-->>Browser: opaque session cookie and redirect to /bff/csrf
    end
```

Normal `/bff/login` uses the same binding step. The first accepted customer login creates the account; duplicate or concurrent callbacks resolve the same unique row. Email is never a join key. A disabled, deleting, or deleted account prevents session issuance. Maintainers remain account-less and cannot use the registration completion path.

## Gate 5: principal-derived ownership

```mermaid
sequenceDiagram
    autonumber
    actor Browser
    participant Filter as BffSessionAuthenticationFilter
    participant Sessions as BffSessionService
    participant Accounts as CustomerAccountService
    participant Access as PrincipalAccessService
    participant Feature as Future private-data service
    participant Repo as Future owner-scoped repository

    Browser->>Filter: request + opaque session cookie
    Filter->>Sessions: resolve(raw handle)
    Sessions->>Sessions: validate hash, status, idle/absolute expiry, CSRF binding
    Sessions->>Accounts: requireActive(accountId, issuer, subject, epoch)
    alt any coordinate differs or account is inactive
        Accounts-->>Browser: generic 401 AUTHENTICATION_REQUIRED
    else exact ACTIVE binding
        Accounts-->>Sessions: ActiveCustomerPrincipal
        Sessions-->>Filter: PrincipalContext from server-side state
        Feature->>Access: requireActiveCustomer(PrincipalContext)
        alt maintainer used on customer operation
            Access-->>Browser: 403 FORBIDDEN
        else customer
            Access-->>Feature: ActiveCustomerPrincipal(accountId, ...)
            Feature->>Repo: findByResourceIdAndAccountId(resourceId, principal.accountId)
            alt absent or belongs to another customer
                Access-->>Browser: 404 NOT_FOUND
            else owned
                Repo-->>Feature: resource
            end
        end
    end
```

The production feature in Gate 5 is the active-account/ownership control itself. The proof is deliberately service-level in this slice because no private customer resource API exists yet. `PrincipalAccessServiceTest`, `CustomerAccountServiceTest`, architecture tests, and the PostgreSQL concurrency test exercise the reusable control without inventing `/api/v1/me` early.

## Current end-to-end actor flow

```mermaid
flowchart TD
    Start{Entry point}
    Start -->|/bff/register| Register[Bounded customer registration]
    Start -->|/bff/login| Login[Existing customer or maintainer login]
    Register --> OIDC[Keycloak Authorization Code + PKCE]
    Login --> OIDC
    OIDC --> Actor{Exactly one supported role?}
    Actor -->|neither or both| Auth401[401 generic authentication failure]
    Actor -->|CUSTOMER| Bind[Create/find account by issuer + subject]
    Actor -->|CATALOG_MAINTAINER| MaintainerSession[Create account-less maintainer session]
    Bind --> Active{Account ACTIVE?}
    Active -->|no| Auth401
    Active -->|yes| CustomerSession[Create customer session with account + epoch]
    CustomerSession --> Request[Later same-origin request]
    MaintainerSession --> Request
    Request --> Resolve[Resolve opaque server-side session]
    Resolve --> Kind{Actor kind}
    Kind -->|customer| Recheck[Recheck exact active account + epoch]
    Recheck -->|failed| Auth401
    Recheck -->|passed| Owner[Owner authority available to future private features]
    Kind -->|maintainer| Catalog{Catalog route?}
    Catalog -->|authorized maintainer route| Relay[Relay server-held access token to Catalog]
    Catalog -->|customer-only operation| Forbidden[403 FORBIDDEN]
```

## Security and rollout notes

- Registration is enabled only by the local `dev` profile and the local Keycloak realm; the base configuration remains disabled, so staging and production fail closed until explicitly enabled.
- The registration limiter trusts only the servlet container's direct peer address and ignores caller-supplied forwarding headers.
- Migration V003 deletes pre-Gate-4 customer sessions because they contain no account or security epoch. Users must authenticate again; maintainer sessions are unaffected.
- The unique `(issuer, subject)` constraint is the concurrency authority. The repository's upsert makes callback replay idempotent without merging by email.
- Session resolution checks the account on every customer request. Changing status or security epoch invalidates existing customer sessions immediately.
- `CustomerOwnedResourceNotFoundException` gives future owner-scoped lookups the same 404 for missing and cross-owner resources.
- Rollback disables `/bff/register` and invalidates customer sessions. The durable account rows remain for a forward fix; they are not destructively removed.

## Verification map

| Objective | Automated evidence |
| --- | --- |
| Hosted registration request is distinct from login | `OidcAuthorizationRequestFactoryTest` |
| Registration start is bounded | `RegistrationRateLimiterTest` |
| Exact active account binding | `CustomerAccountServiceTest` |
| Concurrent callbacks create one account | `CustomerAccountRepositoryTest` with PostgreSQL/Testcontainers |
| Customer/maintainer and missing-account denial | `PrincipalAccessServiceTest` |
| Controllers cannot bypass account services for repositories | `ArchitectureRulesTest` |
| Local OIDC flow exposes `prompt=create` and persists a bound session | `scripts/verify-bff-oidc-flow.sh` |
| Realm registration/default-role/password policy does not drift | `scripts/verify-keycloak-realm-drift.sh` |
