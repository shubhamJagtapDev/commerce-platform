# IDA-DEC-001: Start COM-2 with a deliberately small microservice topology

> **Status:** Accepted — Gate 0 owner review, 2026-08-17  
> **Date:** 2026-08-13  
> **Owners/reviewers:** Product owner, technical owner, security reviewer, performance reviewer  
> **Controlling requirements:** [`CF-PRD-001` v1.0](https://app.notion.com/p/3b6faa3e42dd818d8debd9dfffb883ab), [COM-2](https://shubhamjagtap.atlassian.net/browse/COM-2), explicit product-owner learning direction dated 2026-08-13  
> **Supersession:** Supersedes only the topology portion of the approved Notion “Use a modular monolith first” decision and the one-Spring-application wording in `CF-PERF-DEC-001`; behavior, security, PostgreSQL-first policy, performance targets, evidence rules, and timebox remain in force

## Decision

Start with two application microservices—Identity Access Service and Catalog Service—plus Keycloak and service-owned logical PostgreSQL databases. Keep them in one repository and omit a broker, mesh, Kubernetes, distributed cache, replicas, and service-per-entity decomposition until evidence justifies those additions.

This selection is driven by the product owner's explicit objective to learn microservice engineering. It is not a claim that current scale or product complexity requires microservices.

## Context and design pressure

### Approved facts

- `CF-PRD-001` fixes the customer identity/profile/address behavior, evidence obligations, bounded scale, and Week 1 timebox.
- The approved Notion rationale selects a modular monolith first, with inventory and the payment simulator separate.
- `CF-PERF-DEC-001` and cross-capability scenarios `022`–`028` assume one Spring application and one PostgreSQL primary.
- Security requirements select Keycloak, a same-origin BFF, opaque browser sessions, issuer-and-subject ownership, transaction-local authorization, deny-first deletion, and bounded retention.
- The repository has no executable application or build yet, so there is no sunk implementation topology.

### Latest direction

On 2026-08-13 the product owner explicitly selected microservices from the start to gain experience building and operating real service boundaries, while requiring the result to stay simple, effective, efficient, and fast.

### Evidence about later extraction

A modular monolith can make later extraction safer when it has genuine module APIs, private data ownership, and architecture tests. It does not make extraction automatic:

- a local method call becomes a versioned remote contract with authentication, timeouts, retries, partial failures, and observability;
- one database transaction becomes multiple service-local transactions plus idempotency, outbox, reconciliation, or a saga;
- tables and historical data must be assigned and migrated without violating ownership or availability;
- performance, security, deployment, test, and incident-operating models all change.

This conclusion is consistent with a [systematic mapping study of 114 migration studies](https://doi.org/10.1016/j.infsof.2024.107590), a [stepwise migration case study](https://doi.org/10.1016/j.peva.2024.102411), [AWS decomposition guidance](https://docs.aws.amazon.com/prescriptive-guidance/latest/modernization-decomposing-monoliths/), and [Microsoft guidance on service-owned data and cross-service consistency](https://learn.microsoft.com/en-us/dotnet/architecture/microservices/architect-microservice-container-applications/data-sovereignty-per-microservice).

## Options considered

### Option A — Two small microservices from the start (selected)

- **How it works:** Identity Access owns customer/maintainer BFF sessions and customer account/profile/address/deletion. Catalog owns catalog authorization now and catalog lifecycle under E1. Keycloak remains the identity provider. Each application service has an independent build, runtime, credentials, migrations, and logical database.
- **Benefits:** Provides immediate practice with service/data ownership, network contracts, distributed tracing, timeouts, independent deployment, and cross-system recovery. No later initial extraction is needed for these two boundaries.
- **Costs/risks:** Higher bootstrap and operating effort; local distributed failures; performance baseline changes; no cross-service ACID transaction; potential to consume the Week 1 timebox.
- **Evidence or uncertainty:** The learning objective is explicit. There is no measured production scale need. The exact resource split must be measured rather than guessed.

### Option B — Approved modular monolith first, extract later

- **How it works:** One Spring process contains strongly isolated BFF/customer/catalog modules, one PostgreSQL primary, private schemas/repositories, module contracts, and architecture tests. Extract only after a measured trigger.
- **Benefits:** Lowest operational complexity, local transactions, compatibility with the approved performance environment, and the most delivery headroom for P0 work.
- **Costs/risks:** Does not provide end-to-end microservice operating experience now. Extraction remains substantial work and can be obstructed if module boundaries erode.
- **Evidence or uncertainty:** This is the existing approved product/performance choice and would be the default recommendation if the learning goal were absent.

### Option C — Fine-grained service per feature/entity

- **How it works:** Separate BFF, identity binding, session, profile, address, deletion, authorization, and catalog services.
- **Benefits:** Maximizes the number of visible service interactions.
- **Costs/risks:** Fragments account/deletion/default-address invariants, greatly expands operational surface, creates many distributed transactions, and jeopardizes Week 1 and P0.
- **Evidence or uncertainty:** No scale, team-ownership, security-isolation, or release-cadence evidence justifies these boundaries.

## Comparison

| Criterion | Importance | Option A: two services | Option B: modular monolith | Option C: fine-grained services | Evidence/uncertainty |
|---|---:|---|---|---|---|
| Microservice learning now | High by explicit direction | Strong | Limited until extraction | Strong but noisy | Learning objective is approved direction |
| Week 1 delivery risk | High | Medium | Lowest | Highest | Repo has no bootstrap yet |
| Transactional simplicity | High | Customer invariants remain local; cross-service work explicit | Strongest | Weakest | Data-ownership guidance is well established |
| Approved performance compatibility | High | Requires source/environment revision | Exact match | Requires larger revision | Existing baseline is explicit |
| Operational burden | High | Bounded | Lowest | Excessive | No platform team or automation exists yet |
| Reversibility | Medium | Can co-deploy or merge with migration | Can extract with migration | Expensive consolidation | No application state exists yet |
| Evidence of scale need | Low for learning decision | None | None required | None | Synthetic bounded workload only |

## Rationale

Option A is the smallest topology that satisfies the learning objective honestly. It preserves all customer ownership and deletion invariants inside one Identity Access transaction boundary while making catalog authorization a real independently owned network boundary. A one-repository, one-local-PostgreSQL-server developer experience contains cost without weakening logical ownership.

Option B remains a sound product-delivery choice and would usually be preferred for this bounded first slice. It is not selected because the owner has deliberately prioritized learning the additional concerns now. Option C teaches accidental complexity more than useful architecture and has no evidence-based boundary.

## Consequences

### Positive

- Independent service builds, processes, health, configuration, migrations, and credentials exist from the first executable slice.
- The team must learn contract tests, trace correlation, timeout budgets, remote authorization, idempotency, and reconciliation in a controlled scope.
- Catalog and customer data authorities are physically and logically clear.
- No future extraction is required for the chosen two boundaries.

### Negative/accepted costs

- Bootstrap, CI, local startup, debugging, evidence capture, and deployment are more complex than one process.
- A cross-service request can be unavailable or uncertain even while both databases are correct.
- Performance evidence needs total-system and per-service resource accounting.
- The approved Notion and repository performance/acceptance sources need an explicit synchronized revision.
- The Week 1 hard timebox may force scope reduction or a return to Option B.

### Risks and mitigations

| Risk | Likelihood/impact | Mitigation | Evidence/owner |
|---|---|---|---|
| Microservice bootstrap displaces P0 | Medium/high | Timebox platform work; stop on missed COM-43 gate; retain merge-back path | Product/technical owner |
| Distributed surface expands by habit | Medium/high | Two-service cap; new service requires a decision record and measured authority/team/scale driver | Architecture review |
| One local PostgreSQL server becomes an accidental shared DB | Medium/high | Separate databases, roles, migration histories, and architecture/integration checks prohibiting cross-access | Integration evidence |
| Invalid performance comparison | High/high | Revise environment manifest and scenarios before any claim; report total and per-service resources | Performance reviewer |
| Network auth weakens owner authorization | Low/high | Customer tokens never cross boundary; Catalog validates relayed maintainer token and local grant | Security evidence |
| Unmeasured complexity produces unsupported “scalable” claims | Medium/medium | Preserve no-claim rule until representative evidence passes | Evidence review |

## Reversibility and migration

This is a **two-way door now** because no application data or consumers exist. The topology can be reversed by co-deploying and then merging Catalog into the Identity Access process, preserving HTTP/event contracts as internal ports, migrating the Catalog database into a private schema, and removing network adapters after parity evidence.

It becomes costlier after independent releases, consumers, data volumes, operational policies, or service-specific availability expectations emerge. Reversal must then include a data cutover, contract deprecation, deployment coordination, and performance/security re-baseline.

## Revisit triggers

- COM-43 cannot produce a repeatable two-service local build/start/test path inside its agreed timebox.
- The two-service baseline exceeds the approved total resource envelope after reasonable configuration and the owner will not revise the envelope.
- Service-boundary work causes the hard Week 2 P0 transition to slip.
- A new approved requirement needs an atomic customer-and-catalog invariant, and an outbox/saga would be disproportionate to its value.
- Operating evidence shows the network boundary adds material latency or failures without providing the learning outcome.
- A later team/scale/security requirement justifies splitting another authority; that split requires its own decision.

## Traceability and validation

| Requirement/claim | Design enforcement | Test/evidence |
|---|---|---|
| Explicit microservice learning | Independent builds, runtimes, DB roles, remote contract | Bootstrap evidence and service-level telemetry |
| Simple/effective/fast | Two-service cap; monorepo; shared physical local PG only | Local setup timing and resource manifest |
| `CF-INV-004`, `011` | Customer ownership remains local; Catalog owns final grant | Authorization and zero-state-change matrices |
| Performance targets remain meaningful | Revised multi-process evidence manifest | `SCN-022`–`028` rerun only after sync |
| No unsupported scale claim | Evidence gate in HLD/implementation plan | Review checklist and evidence metadata |

## Teaching note

A modular monolith is not a failed microservice architecture, and microservices are not inherently more scalable or mature. Use network boundaries when their isolation, independent change, ownership, or deliberate learning value exceeds their transaction and operating cost. Good modular boundaries make future extraction safer, but they cannot remove the physics of networks or the work of splitting state.
