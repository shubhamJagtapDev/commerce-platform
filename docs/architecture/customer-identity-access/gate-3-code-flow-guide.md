# Gate 3 COM-46 Catalog-Maintainer Authorization Flow

## Purpose

Gate 3 proves one narrow security property before real Catalog commands are added:

> A signed-in catalog maintainer may reach one explicitly approved Catalog mutation, but a browser, customer, forged request, or maintainer without an active Catalog-owned grant cannot change Catalog state.

The mutation is intentionally a small authorization probe. Later Catalog commands can replace the probe while preserving the same trust boundaries.

## What the gate establishes

```mermaid
flowchart LR
    A["Browser request"] --> B{"Valid BFF session,<br/>CSRF and origin?"}
    B -->|No| X1["Identity Access rejects<br/>401 or 403"]
    B -->|Yes| C{"Session is a<br/>catalog maintainer?"}
    C -->|No| X2["Identity Access rejects<br/>403"]
    C -->|Yes| D["Sanitize request and relay<br/>server-held access token"]
    D --> E{"JWT valid for Catalog?<br/>issuer + sub + aud + azp"}
    E -->|No| X3["Catalog rejects<br/>401"]
    E -->|Yes| F{"Active Catalog grant for<br/>exact issuer + subject?"}
    F -->|No| X4["Catalog rejects<br/>403; no mutation"]
    F -->|Yes| G{"Idempotency key state"}
    G -->|New key| H["Create one probe<br/>201 Created"]
    G -->|Same key and request| I["Return original probe<br/>200 OK"]
    G -->|Same key, different or<br/>incomplete request| X5["Reject 409;<br/>no second mutation"]
```

Identity Access performs an early coarse check. Catalog remains the final authorization owner and does not trust the BFF role by itself.

## Trust boundaries and owned state

```mermaid
flowchart TB
    subgraph Browser["Untrusted browser"]
        BR["Opaque session cookie<br/>CSRF token<br/>probe request"]
    end

    subgraph Identity["Identity Access service"]
        SF["Session, CSRF and origin filters"]
        DB1[("identity_access DB<br/>BFF session + encrypted tokens")]
        GW["One explicit Gateway MVC route<br/>POST authorization-probes"]
        LIM["Header/body limits<br/>one-second deadline<br/>per-instance admission"]
    end

    subgraph Issuer["Keycloak"]
        KC["Authenticates actor<br/>issues signed access token"]
    end

    subgraph Catalog["Catalog service"]
        JWT["JWT resource server validation"]
        AUTH["Transactional grant check"]
        DB2[("catalog DB<br/>grant + idempotency + probe")]
    end

    BR --> SF
    SF <--> DB1
    SF --> GW --> LIM -->|"sanitized request;<br/>server-held bearer token"| JWT
    KC -->|"token stored encrypted<br/>during login"| DB1
    JWT --> AUTH <--> DB2
```

| Component | What it may decide | What it must not decide |
|---|---|---|
| Keycloak | Identity and coarse realm role represented in signed tokens | Whether the actor currently has a Catalog business grant |
| Identity Access | Whether the opaque session is active and classified as a maintainer; whether the request may use the explicit gateway route | Final permission to mutate Catalog state |
| Gateway filters | Limits, sanitation, admission, correlation, deadline, and token relay | Actor identity from caller-supplied headers |
| Catalog JWT validation | Whether the token is authentic and intended for this Catalog/API-client combination | Permission based only on a token role |
| Catalog transaction | Whether the exact `(issuer, subject)` grant is active and whether the command is new or a replay | Customer identity or BFF session lifecycle |

## End-to-end sequence in the current code

```mermaid
sequenceDiagram
    autonumber
    actor Browser
    participant KC as Keycloak
    participant IAF as Identity Access security filters
    participant IDB as identity_access DB
    participant GW as Catalog Gateway route
    participant CATSEC as Catalog JWT security
    participant APP as CreateAuthorizationProbe
    participant CDB as catalog DB

    Browser->>IAF: Start OIDC login
    IAF->>KC: Authorization Code + PKCE request
    KC->>Browser: Keycloak-hosted login
    Browser->>KC: Submit credentials
    KC-->>IAF: Authorization code
    IAF->>KC: Exchange code
    KC-->>IAF: ID token + Catalog-audience access token
    IAF->>IDB: Store hashed session handle,<br/>principal and encrypted token bundle
    IAF-->>Browser: Set opaque commerce-session cookie

    Browser->>IAF: GET /bff/csrf with session cookie
    IAF->>IDB: Resolve active session
    IAF-->>Browser: CSRF token

    Browser->>IAF: POST /api/v1/catalog/authorization-probes<br/>cookie + CSRF + Origin + Idempotency-Key
    IAF->>IDB: Resolve session and trusted PrincipalContext
    IAF->>IAF: Validate CSRF/origin and require maintainer access class
    IAF->>GW: Dispatch only the exact POST route
    GW->>GW: Enforce body/header limits and admission capacity
    GW->>IDB: Re-read active maintainer session<br/>and decrypt server-held access token
    GW->>GW: Remove Cookie, CSRF, caller Authorization,<br/>identity and forwarding headers
    GW->>CATSEC: Single downstream request with approved headers,<br/>bearer token, correlation ID and deadline

    CATSEC->>KC: Load/cached signing keys when required
    CATSEC->>CATSEC: Verify signature, time, issuer, subject,<br/>aud=catalog-api and azp=identity-access-bff
    CATSEC->>APP: Trusted issuer + subject and validated body/key

    APP->>CDB: Lock active grant by exact issuer + subject
    APP->>CDB: Lock/read keyed idempotency record
    alt First valid request
        APP->>CDB: Insert idempotency claim and probe
        APP->>CDB: Complete claim with probe ID in same transaction
        APP-->>Browser: 201 Created with probe ID/version/time
    else Same key and same request
        APP->>CDB: Read original probe
        APP-->>Browser: 200 OK with the same probe
    else No active grant or conflicting key
        APP-->>Browser: 403 Forbidden or 409 Conflict
        Note over APP,CDB: Transaction creates no unauthorized second mutation
    end
```

### Important current behavior

- The browser never receives or supplies the Catalog bearer token. Identity Access retrieves it from the encrypted BFF session.
- The current relay uses the access token already stored in the session; it does not refresh the token in this Gate 3 path.
- The gateway performs exactly one downstream attempt. There is no unsafe-request retry.
- Catalog authorizes the opaque Keycloak `(issuer, subject)` pair against its own grant table. Email and caller-provided role/owner headers are not authority.
- Grant lookup, idempotency decision, and probe mutation run in one Catalog transaction.
- Idempotency keys are HMAC-hashed before persistence; raw keys are not stored.

## Request transformation

```mermaid
flowchart LR
    IN["Inbound browser request<br/><br/>Cookie<br/>X-CSRF-Token<br/>Origin<br/>possible caller Authorization<br/>Idempotency-Key<br/>Content-Type / Accept"]
    FILTER["Identity security +<br/>CatalogGatewayRequestFilter"]
    OUT["Outbound Catalog request<br/><br/>server-held Authorization: Bearer ...<br/>Idempotency-Key<br/>Content-Type / Accept<br/>new X-Correlation-Id<br/>X-Request-Deadline-Millis"]
    DROP["Dropped<br/><br/>Cookie and CSRF<br/>caller Authorization<br/>identity/role/owner headers<br/>untrusted forwarding headers<br/>all other unapproved headers"]

    IN --> FILTER --> OUT
    FILTER --> DROP
```

## Outcome matrix

| Scenario | Deciding layer | Result | Catalog mutation |
|---|---|---:|---:|
| No active BFF session | Identity Access security | `401` | None |
| Active customer session | Identity Access access class | `403` | None; no downstream call |
| Missing/invalid CSRF or origin | Identity Access security | `403` | None; no downstream call |
| Unmatched path or HTTP method | Explicit route policy | `404` | None; no downstream call |
| Missing/oversized body or oversized headers | Gateway request filter | `413` | None; no downstream call |
| Admission capacity exhausted | Gateway admission filter | `429` | None; no downstream call |
| Catalog unavailable | Gateway error mapping | `503` | No confirmed mutation |
| One-second downstream deadline exhausted | Gateway error mapping | `504` | Outcome recovered by replaying the same idempotency key |
| Forged/invalid/wrong-audience token | Catalog JWT resource server | `401` | None |
| Valid token but no active exact Catalog grant | Catalog transaction | `403` | None |
| New valid maintainer command | Catalog transaction | `201` | Exactly one probe |
| Same key and same command | Catalog transaction | `200` | No second probe |
| Same key with conflicting state/request | Catalog transaction | `409` | No second probe |

## Code map

| Responsibility | Main code |
|---|---|
| Route declaration and one-second HTTP client | `CatalogGatewayConfiguration` |
| Route completeness, uniqueness, overlap and target checks | `GatewayRouteValidator`, `GatewayRouteRegistry` |
| Browser request limits, trusted principal check, sanitation and token relay | `CatalogGatewayRequestFilter` |
| Per-instance concurrent admission | `GatewayAdmissionFilter` |
| Active BFF session and encrypted access-token retrieval | `BffSessionAuthenticationFilter`, `BffSessionService` |
| Catalog resource-server boundary | Catalog `SecurityConfiguration`, `CatalogSecurityConfiguration` |
| Final grant and idempotency transaction | `CreateAuthorizationProbe` |
| Catalog-owned persistence | `CatalogMaintainerGrantRepository`, `CatalogCommandIdempotencyRepository`, `CatalogAuthorizationProbeRepository` |
| Schema and constraints | `V002__create_catalog_maintainer_gate.sql` |
| Real actor and replay canary | `scripts/verify-bff-oidc-flow.sh` |

All new Gate 3 persistence repositories are Spring Data JPA repositories. Gate 3 introduces no JDBC-based repository.

## End result

The current code creates a double authorization boundary:

1. Identity Access proves that the browser owns an active maintainer BFF session and permits only the reviewed route.
2. Catalog independently proves that the relayed token is authentic and that its exact subject has an active Catalog-owned grant in the same transaction as the write.

Passing only one boundary is insufficient. This is the reusable security shape intended for future Catalog maintenance commands.
