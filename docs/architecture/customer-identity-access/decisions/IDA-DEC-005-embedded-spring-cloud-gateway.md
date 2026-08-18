# IDA-DEC-005: Embed Spring Cloud Gateway Server Web MVC in Identity Access

> **Status:** Accepted — Gate 0 technical/security review, 2026-08-17; route/filter evidence remains mandatory  
> **Date:** 2026-08-17  
> **Owners/reviewers:** Product owner, technical owner, security reviewer  
> **Controlling requirements:** COM-2, COM-11, COM-43, COM-46, `CF-SEC-IDN-*`, `CF-AUTHZ-*`, `CF-SEC-SES-*`, `CF-SEC-NFR-*`  
> **Supersedes/superseded by:** Supplements `IDA-DEC-001`–`004`; supersedes no decision

## Decision

Embed **Spring Cloud Gateway Server Web MVC** in the existing Identity Access/BFF deployable and use its Java WebMvc.fn route DSL as the routing foundation for calls to Catalog and future approved services. This adds no gateway deployable, database, Redis instance, or distributed transaction.

Gateway routing is an infrastructure adapter. The existing Identity Access `auth`, `customeraccount`, `deletion`, and `securityaudit` modules, custom PostgreSQL `bff_session`, JDBC transaction model, and browser OIDC/CSRF flow remain authoritative. Downstream services continue to make the final authorization decision for their data and commands.

## Context and design pressure

- The public Identity Access/BFF already owns the browser-specific OIDC flow, opaque session, CSRF controls, token refresh, and safe principal mediation.
- Catalog is currently the only downstream application service, but Cart, Order, Inventory, Payment, and other domain authorities may later expose independently classified APIs.
- Continuing with hand-written path matching and proxy code for every downstream service would duplicate routing, sanitation, deadline, rate, and observability behavior inside security-sensitive code.
- The existing persistence design deliberately prefers explicit blocking JDBC transactions. Selecting a reactive gateway would force either a reactive persistence rewrite or a carefully bounded blocking bridge without a product requirement for either change.
- Spring Cloud Gateway Server Web MVC is built on Spring Boot and WebMvc.fn, works with traditional Servlet runtimes, and can be embedded in an application. The Java API represents routes as `RouterFunction` instances with gateway handler and filter functions.

This is a technical routing decision only. It does not approve product-search/detail contracts, introduce an administrator actor, or change which service owns authorization.

## Options considered

### Option A — Embedded Spring Cloud Gateway Server Web MVC with Java routes (selected)

- **How it works:** Identity Access registers explicit WebMvc.fn gateway routes with method/path predicates and route-specific handler filters. Local BFF and customer-account controllers remain in the same servlet application.
- **Benefits:** Reuses a supported routing/filter foundation, preserves JDBC, centralizes browser-edge controls, and avoids another deployable.
- **Costs/risks:** Identity Access remains a public-edge choke point; servlet/filter ordering and route-policy drift need strong tests; gateway framework upgrades become compatibility-sensitive.
- **Evidence or uncertainty:** Official Spring documentation supports embedded Server MVC and Java routes. Exact Spring Boot/Cloud versions remain a setup decision and must use a compatible release train.

### Option B — Continue the custom Catalog proxy

- **How it works:** Maintain hand-written request matching, forwarding, header filtering, deadlines, and error mapping for each downstream path.
- **Benefits:** Smallest dependency surface for one Catalog route and full local control.
- **Costs/risks:** Cross-cutting behavior and tests grow repeatedly with each service; subtle inconsistencies can become security defects.
- **Evidence or uncertainty:** Viable for the current single route, but it does not provide the desired routing foundation for planned service growth.

### Option C — Embedded Spring Cloud Gateway Server WebFlux

- **How it works:** Run reactive gateway routes and filters on the WebFlux stack.
- **Benefits:** Non-blocking request processing and strong fit for a routing-only gateway with high concurrent I/O.
- **Costs/risks:** Conflicts with the current JDBC-first request and transaction model; blocking session/account work must be rewritten with R2DBC or isolated on bounded schedulers.
- **Evidence or uncertainty:** No measured concurrency or latency need justifies changing the persistence model solely for routing.

### Option D — Separate gateway deployable

- **How it works:** Deploy an independent public gateway in front of a private Identity Access service and all other services.
- **Benefits:** Independent edge scaling, release cadence, and trust-zone isolation.
- **Costs/risks:** Adds a deployment, network hop, failure domain, configuration authority, workload identity requirement, and coordination between gateway and BFF security state.
- **Evidence or uncertainty:** No current team, scale, trust-zone, or release-cadence evidence pays for the additional boundary.

## Comparison

| Criterion | Importance | Embedded MVC | Custom proxy | Embedded WebFlux | Separate gateway | Evidence/uncertainty |
|---|---:|---|---|---|---|---|
| Preserve session/account JDBC model | Highest | Strong | Strong | Weak without bridge/rewrite | Strong behind another hop | Current LLD/schema are JDBC-oriented |
| Future route/filter consistency | High | Strong | Weakening as routes grow | Strong | Strong | Future services are directional, not yet approved contracts |
| Operational simplicity | High | One existing deployable | One existing deployable | One deployable, new runtime model | Additional deployable | Bootstrap has two application services |
| Security-policy reviewability | Highest | Explicit typed routes and tests | Custom per path | Explicit reactive routes and tests | Split configuration/ownership | Final authorization remains downstream |
| Reversibility | Medium | High | High | Medium | Medium | No gateway-owned durable state |

## Rationale

Server Web MVC supplies the routing and filter foundation the public BFF needs without changing the transaction or persistence model selected for identity and customer state. The Java route DSL is preferred over dynamic or database-backed routes because route declarations remain compiled, versioned, reviewable, and testable beside their access classification.

This choice deliberately stops at the edge. Identity Access may authenticate a browser session and reject an obviously wrong actor, but Catalog still validates the relayed maintainer token and its current Catalog-owned grant in the same local transaction as the mutation.

## Consequences

### Positive

- New approved downstream APIs can reuse one route/filter foundation instead of copying proxy code.
- Local BFF and customer APIs retain their current controllers, servlet security chain, JDBC repositories, and transactions.
- Header sanitation, correlation, deadlines, request limits, coarse admission control, and edge error mapping become consistently testable.
- A future `PUBLIC` route can skip session/CSRF authentication without skipping sanitation, limits, observability, or service-owned visibility rules.

### Negative/accepted costs

- Identity Access remains a public-edge bottleneck and correlated failure point for proxied services.
- Route and servlet-filter ordering become security-sensitive configuration.
- Spring Boot and Spring Cloud Gateway must be upgraded as a compatible release train.
- Per-instance coarse rate limits are not a cluster-wide quota and become less exact if Identity Access is replicated.

### Risks and mitigations

| Risk | Likelihood/impact | Mitigation | Evidence/owner |
|---|---|---|---|
| A route omits or weakens access policy | Medium/critical | Typed route declaration requires access class, method, path, target, deadline, and size policy; startup validator rejects incomplete/ambiguous routes | Route manifest and startup tests — Identity Access |
| Browser credentials leak downstream | Low/critical | Strip cookies, CSRF, inbound authorization, identity, and untrusted forwarding headers; construct maintainer authorization only from the server-side session | Canary/header contract tests — security reviewer |
| Gateway becomes sole authorization layer | Medium/critical | Mandatory Catalog JWT validation and transaction-local grant check; future services own their policy | Actor/grant/TOCTOU tests — service owner |
| Built-in token relay creates a second session authority | Medium/high | Do not use its default in-memory authorized-client store; implement relay through existing `bff_session` and token ports | Restart/session-authority tests — Identity Access |
| Automatic retry duplicates a command | Medium/high | No gateway retry for unsafe methods; Catalog owns mutation idempotency and uncertain-outcome recovery | Lost-response and duplicate tests — Catalog |
| Per-instance limiter is mistaken for global protection | Medium/medium | Document scope in configuration, evidence, and runbook; keep strict login abuse policy in Identity Access/Keycloak | Multi-instance review trigger — technical/security owner |

## Security boundary and request contract

Every gateway route must declare a unique route ID, explicit HTTP methods, explicit path predicates, target service, `PUBLIC`/`CUSTOMER`/`MAINTAINER` access class, deadline, and request-size policy. An unrestricted catch-all under `/api/v1/catalog/**` is prohibited; only E1-approved method/path contracts are registered.

The common processing order is:

1. Normalize trusted-proxy information; establish correlation, remaining deadline, and request limits.
2. Resolve the opaque BFF session and validate status, idle/absolute expiry, account status, and security epoch where the route requires authentication.
3. Validate CSRF, Origin/Referer, and Fetch Metadata for unsafe cookie-authenticated requests.
4. Enforce the route's coarse access class.
5. Remove browser cookies, CSRF, caller authorization, spoofable identity, and untrusted forwarding headers from the downstream request.
6. For an approved maintainer route only, resolve/refresh the encrypted server-held token and construct the Catalog `Authorization` header.
7. Forward within the remaining one-second Catalog budget; do not automatically retry unsafe commands.
8. Sanitize the response and record low-cardinality route, outcome, and latency signals.

An unmatched method/path returns a generic `404`; size rejection returns `413 REQUEST_TOO_LARGE`; admission rejection returns `429 RATE_LIMITED` with `Retry-After`; unavailable target returns `503 DEPENDENCY_UNAVAILABLE`; exhausted downstream deadline returns `504 GATEWAY_TIMEOUT`.

## Reversibility and migration

This is a **two-way door** because the gateway owns no durable state or business authority. To revert, preserve the route and filter contracts, replace the Gateway MVC adapter with explicit HTTP controllers/clients, run parity contract tests, switch the route registration, and remove the dependency.

To extract a separate gateway later, first stabilize the public route manifest and signed/workload-authenticated principal contract, deploy the external gateway in shadow/parity mode, move routes incrementally, and keep Identity Access authoritative for browser sessions until a separately approved session-boundary migration exists.

## Revisit triggers

- Identity Access and proxied traffic require independent scaling after measured CPU, thread-pool, connection, or p99 latency evidence.
- A separately owned platform/edge team and release cadence emerges.
- An approved trust-zone requirement requires browser tokens/sessions and customer PII to run in different processes.
- Gateway changes repeatedly cause unrelated Identity Access deployments or availability incidents.
- Multiple replicas make the per-instance limiter insufficient for an approved abuse/fairness SLO, justifying a reviewed shared edge/store.
- Approved public-read caching, dynamic service discovery, or safe degraded behavior has evidence exceeding its infrastructure and stale-data cost.

## Traceability and validation

| Requirement/claim | Design enforcement | Test/evidence |
|---|---|---|
| Browser sees no OAuth bearer token | Custom opaque session and outbound-only maintainer relay | Browser/network/storage scan |
| Customer cannot reach maintainer commands | `MAINTAINER` route class plus no customer-token relay | Anonymous/customer/maintainer route matrix |
| Catalog owns final authorization | JWT validation plus local grant check beside mutation | Forged claim and concurrent revoke/write tests |
| Routes fail closed | Typed explicit route declaration and startup validator | Duplicate/missing/overlap/catch-all startup tests |
| Gateway does not duplicate mutations | Unsafe methods have zero automatic retries | Timeout/lost-response test with one downstream attempt |
| No new durable authority | No gateway table, Spring Session store, Redis, or route database | Schema diff and restart tests |

## Teaching note

An API gateway is an edge policy and routing mechanism, not a business-authorization owner. Embedding it is appropriate while browser session handling and routing share one team, deployment, and scaling profile. A separate gateway becomes preferable only when independent operations, scaling, or trust-zone evidence is strong enough to pay for another network and security boundary.

## References

- [Spring Cloud Gateway Server Web MVC starter and runtime](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webmvc/starter.html)
- [Spring Cloud Gateway Server Web MVC Java Routes API](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webmvc/java-routes-api.html)
- [Writing custom MVC predicates and filters](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webmvc/writing-custom-predicates-and-filters.html)
- [Server MVC TokenRelay behavior and default store limitation](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webmvc/filters/tokenrelay.html)
