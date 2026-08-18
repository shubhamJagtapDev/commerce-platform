# IDA-DEC-003: Relay only maintainer tokens to Catalog and authorize locally

> **Status:** Accepted for implementation — Gate 0 security review, 2026-08-17; COM-46 negative-security evidence remains mandatory  
> **Date:** 2026-08-13  
> **Owners/reviewers:** Technical owner, security reviewer  
> **Controlling requirements:** `CF-SEC-IDN-002`–`004`, `CF-AUTHZ-001`–`002`, `CF-INV-004`, `011`, COM-11/COM-43/COM-46  
> **Supersedes/superseded by:** None

## Decision

For a maintainer catalog request, the Identity Access BFF relays the server-held Keycloak access token over private TLS to Catalog. Catalog independently validates the token for its exact issuer, signature, algorithm, audience, authorized party, and time, then checks a Catalog-owned active maintainer grant in the same database transaction as the protected catalog write.

Customer access tokens never cross this boundary. Browser cookies, CSRF tokens, refresh tokens, identity tokens, caller-supplied roles, and caller-supplied owner IDs are never forwarded to Catalog.

## Context and design pressure

- The browser must receive only an opaque same-origin BFF cookie; OAuth tokens remain server-side.
- COM-11 requires a real maintainer OIDC/BFF boundary, not mock identity or a bearer shortcut.
- Catalog is an independently deployable service and final owner of catalog authorization and state.
- A BFF authorization decision alone cannot safely grant a Catalog write; edge configuration and grants can change concurrently.
- The approved security wording explicitly prohibits customer bearer tokens outside the BFF except conformance tests. It does not explicitly approve a maintainer-token private hop, so this interpretation requires human security approval.

## Options considered

### Option A — Relay the original maintainer access token (selected conditionally)

- **How it works:** Identity Access resolves the opaque session and coarse authority, sends the Keycloak access token only to a private Catalog audience, and Catalog performs local JWT validation plus Catalog-grant authorization.
- **Benefits:** Uses a standard issuer credential; no custom signing protocol; Catalog can authenticate the exact subject without trusting arbitrary headers or a live Identity Access callback.
- **Costs/risks:** A bearer token exists on another server hop; audience/configuration and redaction must be exact; revocation of a self-contained token has bounded residual life.
- **Evidence or uncertainty:** Matches real resource-server practice and Keycloak/Spring support, but the project security reviewer must approve the maintainer-only exception.

### Option B — Trust identity headers from the BFF

- **How it works:** Identity Access sends issuer, subject, and roles in HTTP headers; Catalog trusts the network/proxy.
- **Benefits:** Simple and avoids forwarding a provider token.
- **Costs/risks:** Any bypass or header-confusion issue becomes impersonation; requires a strong workload-identity/signature design anyway; role headers become stale authority.
- **Evidence or uncertainty:** Unacceptable with plain headers and no established service-identity platform.

### Option C — Mint a custom short-lived internal assertion

- **How it works:** Identity Access signs a narrowly scoped, Catalog-audience assertion; Catalog validates the internal key.
- **Benefits:** Can minimize claims and expiry and avoid relaying the provider token.
- **Costs/risks:** Creates a second token issuer, key lifecycle, claim profile, replay model, and incident surface that the project owns.
- **Evidence or uncertainty:** Could become preferable if trust zones or token-exchange requirements mature; unjustified at bootstrap.

### Option D — Introspect every request with Keycloak/Identity Access

- **How it works:** Catalog sends the token or subject to an authority for a live answer before each write.
- **Benefits:** Fresh central session/role state.
- **Costs/risks:** Adds a synchronous dependency and latency to every write, expands failure amplification, and still does not replace Catalog's business grant.
- **Evidence or uncertainty:** No immediate-revocation requirement justifies the dependency; current access-token maximum is five minutes.

## Comparison

| Criterion | Importance | A: token relay | B: trusted headers | C: custom assertion | D: introspection | Evidence/uncertainty |
|---|---:|---|---|---|---|---|
| Exact subject authentication | Highest | Strong with validation | Weak without signed workload identity | Strong if implemented correctly | Strong while dependency available | Token issuer is Keycloak |
| Custom security protocol | High | None | Usually hidden/custom trust | Highest | Moderate | Small team/bootstrap |
| Synchronous availability dependency | High | None after token received | Identity edge only | Identity edge only | Every Catalog request | Latency budget is bounded |
| Claim minimization | Medium | Provider token may contain more claims | Configurable headers | Strongest | Depends on response | Log/trace redaction still mandatory |
| Revocation freshness | High | ≤5-minute residual plus local grant | Session dependent | Assertion TTL | Freshest | Catalog grant provides immediate local business revoke |
| Existing security-source fit | Highest | Needs explicit maintainer interpretation | Does not meet fail-closed proof | New unapproved issuer | Task says stop for per-request introspection | Human review required |

## Rationale

Option A is the smallest standards-based way for an independent Catalog Service to authenticate the exact maintainer subject without trusting mutable headers or inventing another issuer. The Catalog-local grant solves business-authorization freshness: revoking the grant causes the same transaction's write to fail even if a previously minted Keycloak token remains cryptographically valid.

The choice is conditional because “tokens remain server-side/BFF-only” can be read more strictly. If the reviewer prohibits the hop, Option C should be evaluated with Keycloak token exchange or a reviewed internal assertion rather than silently falling back to trusted headers.

## Consequences

### Positive

- The browser never receives or forwards a bearer token.
- Catalog authenticates the real issuer/subject and owns the final authorization decision.
- Identity Access need not be synchronously called from Catalog.
- Catalog grant revocation and write preconditions share one local transaction boundary.

### Negative/accepted costs

- The maintainer access token crosses one private TLS hop and exists in two process memories.
- Keycloak client/audience/claim configuration becomes a versioned dependency of both services.
- Catalog must implement strict resource-server validation and safe key/discovery caching.
- Self-contained token revocation remains bounded by its ≤5-minute lifetime, although local Catalog grant revocation is immediate.

### Risks and mitigations

| Risk | Likelihood/impact | Mitigation | Evidence/owner |
|---|---|---|---|
| Token logged or traced | Low/critical | Header redaction at both services/proxy; token canary; no HTTP body echo | Security test/human review |
| Token accepted by wrong service | Low/high | Exact Catalog audience and client/authorized-party assertions | Negative claim matrix |
| BFF forwards customer token | Low/critical | Actor-specific proxy policy; Catalog audience/role/grant all required | Customer-to-Catalog negative test |
| Stale role enables write | Low/high | Role is only a hint; Catalog-local grant locked/checked with write | Concurrent grant-revoke/write test |
| Key rotation/discovery outage | Medium/medium | Bounded standards-compliant key cache; unknown key fails closed; readiness/metrics | Config-drift/failure evidence |
| Request replay duplicates a write | Medium/high | Catalog-owned business idempotency key/fingerprint | Lost-response/replay test |

## Reversibility and migration

This is a **two-way door** while Catalog has one internal caller. Preserve a `CatalogPrincipalAuthenticator` port so the adapter can change from Keycloak JWT validation to token exchange or a reviewed internal assertion. Run old/new authenticators in non-authoritative shadow comparison, validate subject/action parity without logging identity, cut over behind a configuration version, then remove the old path.

Never migrate by temporarily trusting unsigned identity/role headers.

## Revisit triggers

- Security review rejects a maintainer bearer token outside the BFF.
- A mobile, third-party, or non-browser client enters approved scope.
- Catalog needs multiple trusted callers or a distinct workload identity plane.
- Measured JWT/JWK behavior causes material latency or availability issues.
- An approved requirement needs revocation faster than both the local Catalog grant and five-minute token bound provide.
- Token payload minimization cannot be achieved in the Keycloak client configuration.

## Traceability and validation

| Requirement/claim | Design enforcement | Test/evidence |
|---|---|---|
| No browser bearer token | Opaque cookie; Authorization header constructed server-side only | Browser storage/network scan |
| Maintainer-only Catalog authority | Exact token claims plus active Catalog grant | COM-46 actor/action matrix |
| Customer cannot mutate Catalog | No customer-token proxy path; wrong audience/role/grant fails | `403`, zero state/field evidence |
| No TOCTOU grant | Grant check and write in one Catalog DB transaction | Deterministic revoke/write concurrency test |
| Failure commits nothing | Bounded validation/key/dependency failure before write | Fault-injection evidence |

## Teaching note

Authentication context may cross a service boundary, but authority should not. The receiving data owner must authenticate the assertion and apply its own current policy beside the write. A gateway check is defense in depth; it is not a substitute for resource-owner authorization.
