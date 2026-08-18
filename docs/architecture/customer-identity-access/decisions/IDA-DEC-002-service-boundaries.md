# IDA-DEC-002: Keep auth and customer account as modules; isolate catalog authority

> **Status:** Accepted — Gate 0 owner review, 2026-08-17  
> **Date:** 2026-08-13  
> **Owners/reviewers:** Technical owner, product owner  
> **Controlling requirements:** `CF-IDN-*`, `CF-ACC-*`, `CF-ADDR-*`, `CF-AUTHZ-*`, `CF-INV-004`, `005`, `011`, COM-11–COM-17  
> **Supersedes/superseded by:** None

## Decision

Place the same-origin BFF for both customers and catalog maintainers, customer binding, session, profile, address, and deny-first deletion capabilities in one Identity Access Service. Keep Catalog as a separate service and authoritative database; Keycloak remains the external credential and identity provider.

## Context and design pressure

Customer ownership is derived from a BFF session bound to `(issuer, subject)`. Profile, addresses, sessions, account status, and deletion share security-epoch, owner-scope, and immediate-deny invariants. Splitting them would turn one local deletion/default/ownership operation into a distributed workflow with no independent scale, team, release, or security requirement.

Catalog has a distinct business authority, a different actor mutation policy, a separate delivery epic, and no legitimate access to customer PII. Making it the real network boundary provides useful service isolation without fragmenting the account aggregate.

## Options considered

### Option A — Identity Access plus Catalog (selected)

- **How it works:** One cohesive customer/security process and database; one separate Catalog process/database; Keycloak outside both.
- **Benefits:** Keeps customer and deletion invariants transactional; provides a genuine catalog authorization boundary; only two application services to operate.
- **Costs/risks:** Identity Access has several internal modules and could become broad if future capabilities are added without discipline.
- **Evidence or uncertainty:** Current requirements share account ownership and deletion lifecycle; Catalog is already a distinct domain authority.

### Option B — Separate BFF/session and Customer Account services

- **How it works:** BFF owns login/session; Account owns profile/address/deletion; every private call crosses the network with propagated identity.
- **Benefits:** Independent BFF deployment and a narrower public-edge process.
- **Costs/risks:** Every customer operation adds a hop; subject/session/account deny decisions span services; deletion and credential revocation need more coordination.
- **Evidence or uncertainty:** No different scaling, release, trust-zone, or team owner currently exists.

### Option C — One service for each profile, address, session, and deletion capability

- **How it works:** CRUD-oriented fine-grained services and several databases.
- **Benefits:** Superficial independent deployment for each feature.
- **Costs/risks:** Breaks cohesive invariants, creates many remote calls and sagas, multiplies security surfaces, and exceeds the timebox.
- **Evidence or uncertainty:** No justifying evidence.

## Comparison

| Criterion | Importance | Option A | Option B | Option C | Evidence/uncertainty |
|---|---:|---|---|---|---|
| Account/deletion invariant locality | Highest | Strong | Weaker | Weakest | Requirements share account state/epoch |
| Useful microservice learning | High | Strong at Catalog boundary | Strong | High volume, low signal | Learning goal needs quality, not count |
| Request latency/failure surface | High | Smallest distributed surface | Every private request adds hop | Largest | Bounded latency targets apply |
| PII isolation from Catalog | Highest | Strong | Strong | Strong but fragmented | Approved security rule |
| Future independent change | Medium | Catalog independent; customer modules explicit | More independent | Maximum but unjustified | Future needs unknown |

## Rationale

Services are derived from authoritative rules and transaction boundaries, not tables. Customer session/account/profile/address/deletion are one security and lifecycle authority. Catalog is the smallest separate authority that supplies a meaningful microservice boundary and protects customer PII by construction.

## Consequences

### Positive

- Owner lookup, active/deleting status, session security epoch, profile/address mutation, and deletion acceptance stay in local transactions.
- Catalog cannot query customer tables and cannot infer a customer owner.
- Internal customer modules can be architecture-tested and extracted later only if evidence develops.

### Negative/accepted costs

- The Identity Access Service must maintain strict internal package boundaries to avoid becoming an unstructured identity monolith.
- Catalog calls require versioned contracts, tracing, timeout behavior, and remote authentication.
- Coarse catalog rejection exists at the BFF, but final authorization must be repeated in Catalog.

### Risks and mitigations

| Risk | Likelihood/impact | Mitigation | Evidence/owner |
|---|---|---|---|
| Identity Access accumulates unrelated features | Medium/high | Explicit module ownership; new domain requires boundary review | Architecture tests/review |
| Internal module reaches another repository | Medium/high | Ports-and-adapters dependency rules and ArchUnit tests | CI evidence |
| Catalog trusts edge authorization | Low/high | Mandatory token validation and local grant check in Catalog transaction | COM-46 evidence |
| Shared local PG weakens ownership | Medium/high | Separate DBs/users; deny cross-service grants | Integration test/config review |

## Reversibility and migration

This is a **two-way door before data volume and independent releases grow**. A customer module can be extracted by stabilizing its port, introducing a remote adapter, creating an owned database, backfilling verified state, dual-reading only if required, cutting over, and removing the old tables. Catalog can be merged by reversing that sequence.

The decision deliberately retains internal module boundaries so topology can change without first discovering domain ownership. Extraction is still a migration, not a configuration toggle.

## Revisit triggers

- A separately owned team and release cadence emerges for BFF/session or customer profile/address.
- Measured CPU, connection, or latency behavior needs independent scaling that cannot be addressed inside the current process.
- An approved trust-zone requirement demands separate handling of tokens and customer PII.
- Identity Access changes repeatedly create unrelated deployment risk across modules.
- A proposed extraction cannot identify an authoritative database owner or safe migration sequence; reject the split until it can.

## Traceability and validation

| Requirement/claim | Design enforcement | Test/evidence |
|---|---|---|
| Owner comes from `(iss, sub)` | BFF/session/account share authoritative DB boundary | Login and cross-owner tests |
| At most one default | Address aggregate and account lock remain local | Concurrency integration test |
| Deny before deletion response | Account/session/profile/address/workflow in one transaction | Phase-fault and restart evidence |
| Maintainer has no customer authority | Catalog has no customer DB/client capability | Path/field/state matrix |
| Services own data | Separate DB roles and migrations | Cross-access denial test |

## Teaching note

“Microservice” describes an independently owned capability, not an entity-sized process. Keep invariants together unless a stronger ownership, isolation, change-rate, or scale driver pays for remote coordination. Internal modularity and deployment topology are related but separate decisions.
