# Customer Identity and Access — Design Review

> **Review status:** Completed against the repository staff-engineer checklist  
> **Artifacts reviewed:** [`hld.md`](hld.md), [`lld.md`](lld.md), [`schema-design.md`](schema-design.md), [`implementation-plan.md`](implementation-plan.md), `IDA-DEC-001`–`005`  
> **Review date:** 2026-08-17  
> **Reviewer:** Staff-engineer design pass; required human approvals remain listed below

## 1. Review verdict

**Verdict: Conditionally ready.**

The design is complete enough for the next activity—planning a reproducible local development setup—and it prevents unsafe implementation inference by naming owners, contracts, transactions, gateway route/filter policy, failure semantics, tests, and stop conditions. Embedding Spring Cloud Gateway Server Web MVC introduces no unresolved service/data boundary or schema change. It is not implementation-ready until the approved sources are synchronized for the microservice topology, the compatible Spring release train/filter ordering is proven during setup, and the maintainer credential-propagation decision receives human security approval. Full account-deletion completion also depends on the E3 Cart consumer.

## 2. Material findings

| Severity | Finding/evidence | Consequence if unresolved | Required correction/owner | Blocks |
|---|---|---|---|---|
| High | The approved Notion decision and `CF-PERF-DEC-001` say modular monolith/one Spring app; 2026-08-13 owner direction selects microservices | Performance/acceptance results could be reported against a false environment or architecture history could be lost | Accept `IDA-DEC-001`; mark the topology clauses superseded while preserving rationale; revise `CF-XCAP-SCN-022`–`028` environment wording — product/architecture/performance owners | Implementation-ready verdict and performance claims; not local setup planning |
| High | Maintainer-only Keycloak access-token relay is a proposed interpretation; strict BFF-only wording could reject it | An unreviewed bearer hop could violate the security baseline | Approve `IDA-DEC-003` or select reviewed token-exchange/internal-assertion alternative; never use trusted headers — security owner | COM-46 sign-off |
| High | Cart owns future account-cart data but no E3 deletion consumer exists yet | COM-52/54 cannot prove complete Week 1 footprint deletion or 24-hour reconciliation | Version/link `AccountDeletionAccepted.v1` consumer/inbox work to E3 — E3 owner | COM-16/COM-54 Done |
| Medium | Proposed 24-hour ordinary idempotency retention is not product-approved | Replay window/API behavior could change after implementation | Approve before API freeze and evidence cleanup test — product/technical owner | API freeze, not bootstrap |
| Medium | Proposed 90-day deletion ledger needs privacy/backup-horizon approval | Too-short retention could permit resurrection; too-long retention violates minimization | Validate 30-day backup arithmetic, approve/purge at 90 days — privacy/security owner | COM-54 sign-off |
| Medium | Key management, migration library, exact Spring/JDK/build versions and reproducible commands are absent because no app exists | Session persistence/bootstrap cannot be implemented reproducibly yet | Select in the next local-setup plan; write commands only after repository bootstrap — technical owner | Real session/migration implementation, not architecture approval |
| Medium | `IDA-DEC-005` selects embedded Gateway Server MVC, but the repository has no build proving the Spring Boot/Cloud release pair or servlet/security/form-filter ordering | An incompatible release or incorrect filter order could prevent startup, corrupt forwarded form bodies, or weaken route policy | Select an official compatible release train; run enabled/disabled context, route-startup, body-forwarding, and SecurityFilterChain ordering tests — technical/security owner | COM-46 implementation/sign-off, not design/bootstrap planning |
| Medium | One physical local PostgreSQL server will host service-owned databases | Resource contention/shared failure domain can distort service evidence | Separate roles/databases; deny cross-access; record per-service pools and total server resources — technical/performance owner | Performance claim |

No critical design defect or unresolved product-behavior ambiguity was found. The high findings are bounded approval/source/dependency gates with explicit safe defaults and stop conditions.

## 3. Shared-readiness review

| Check | Result | Evidence/correction |
|---|---|---|
| Artifact status and approved source stated | Pass | Every artifact names proposed/conditional status, `CF-PRD-001`, COM-2, and date |
| Requirement/fact/assumption/risk separated | Pass | HLD source table; schema assumptions; plan risk table |
| Stable IDs preserved | Pass | `CF-*`, COM IDs, `IDA-DEC-*`, scenario/evidence mappings |
| Significant requirements map to owner and evidence | Pass | HLD §3/18, LLD §2/14, plan gates |
| Goals/non-goals/constraints/excluded scope | Pass | HLD §2; plan stop conditions |
| Authoritative data/rule owners | Pass | Keycloak vs Identity Access vs Catalog responsibility/data tables |
| Success/invalid/duplicate/concurrent/timeout/partial/uncertain/restart/recovery/deletion | Pass | HLD scenarios/flows; LLD algorithms; plan gates/test matrix |
| Decisions/alternatives/trade-offs/revisit triggers | Pass | `IDA-DEC-001`–`005` |
| Product/security behavior changes escalated | Conditional pass | Topology and token relay explicitly gated; no silent change |
| Unsupported claims avoided | Pass | Microservices chosen for learning, not scale; evidence required before performance/scalability claim |

## 4. Boundary and dependency review

| Check | Result | Evidence/correction |
|---|---|---|
| Boundaries derive from authorities/invariants | Pass | Customer session/account/deletion stay local; Catalog authority is separate |
| Repositories/entities/private rules stay private | Pass | LLD package/dependency policy and architecture tests |
| Commands/queries/events defined | Pass | LLD §5, plan §4 |
| Dependency direction enforced | Pass | Ports/adapters, no cross-service Java imports, schema credentials |
| Logical versus physical topology separated | Pass | Separate logical DBs on one optional local server |
| Embedded gateway is not misrepresented as another service | Pass | `IDA-DEC-005` and HLD deployment model keep one Identity Access artifact/process |
| Network boundary justified | Conditional pass | Explicit learning goal and distinct Catalog authority; revisit if Week 2/P0 is threatened |
| Shared kernel small/domain-neutral | Pass | Clock/correlation/problem only |
| Contract compatibility/versioning | Pass | OpenAPI/event rules and `AccountDeletionAccepted.v1` |

## 5. Data and schema review

| Check | Result | Evidence/correction |
|---|---|---|
| Table purpose/owner/keys/types/nullability/defaults/constraints | Pass | Schema §6 and representative DDL |
| Identity/owner/default/lifecycle invariants enforced strongly | Pass | `(issuer,subject)` unique, owner-first address key, partial default index, status checks |
| Access paths map to indexes/sequential-scan rationale | Pass | Schema §4/8/13 |
| Index order/selectivity/cost stated | Pass | Schema §8 |
| Transaction/isolation/lock/order/deadlock stated | Pass | Schema §9 and LLD §8 |
| Idempotency scope/fingerprint/replay/concurrency/expiry | Conditional pass | Fully designed; 24-hour expiry awaits approval |
| State/projection/cache distinctions | Pass | No cache/projection initially; service DBs authoritative |
| Gateway/session/route authority duplication avoided | Pass | No gateway table, Spring Session/OAuth client schema, Redis, or route database; `bff_session` remains sole authority |
| PII/secrets/retention/deletion/backup/restore | Conditional pass | Data lifecycle explicit; deletion ledger duration awaits approval |
| Additive migration/verify/cutover/rollback/cleanup | Pass | Schema §12; plan rollback rules |
| Premature partition/shard/JSON/denormalization avoided | Pass | No partition/shard/cache; JSON only versioned outbox payload |

## 6. API, event, reliability, and operations review

| Check | Result | Evidence/correction |
|---|---|---|
| Authentication/authorization/owner/validation/rate semantics | Pass | HLD/LLD contracts and plan responsibility table |
| Route/access-policy completeness and deny-by-default | Pass with implementation gate | Typed Java route spec requires ID/method/path/target/access/deadline/size; startup rejects missing/overlap/catch-all |
| Error classes distinguish all required outcomes | Pass | LLD problem table; plan semantic failures |
| Mutation idempotency/safe retry | Pass | Key/fingerprint/resource outcome; no blind unsafe retry |
| Pagination ordering/tie-breakers | Not applicable | Bounded Week 1 address collection; decision stated |
| Internal/secret/cross-owner leakage prevented | Pass | Generic denial, no owner transport, allowlisted audit/canary gates |
| Gateway header/token leakage prevented | Pass with COM-46 evidence | Outbound sanitation strips Cookie/CSRF/caller authorization/identity/forwarding headers; custom relay uses persistent session token port |
| Event owner/schema/key/delivery/dedupe/compatibility/retention | Pass | `AccountDeletionAccepted.v1` and outbox semantics; consumer remains dependency |
| Atomic state/event publication | Pass | Identity Access deletion transaction inserts outbox |
| Timeout budgets and retry owner | Pass with setup validation | HLD §10–11, LLD §10, and plan reliability rules; measure exact resource behavior later |
| Retry amplification/degraded behavior | Pass | Gateway unsafe retries explicitly disabled; no nested/blind mutation retry; fail closed; deletion durable retry |
| Bulkhead/circuit-breaker decision | Pass with measurement trigger | Bounded dependency/worker/DB pools selected; circuit breakers deferred until cascading-failure evidence and a safe open state exist |
| Reconciliation/operator actions | Pass | Deletion lease/backoff/ATTENTION/alert/runbook |
| Health/readiness dependency semantics | Pass | Setup gate requires authoritative DB readiness and restore gate |
| Logs/metrics/traces/alerts/runbooks without PII | Pass | HLD/LLD observability and plan canary rules |
| Deployment/config/flags/rollback/DR | Pass | LLD configuration and plan rollout/restore rules |
| Gateway failure behavior | Pass | Generic `404`, `413`, per-instance `429`, connect `503`, deadline `504`; no partial route set or stale authority |
| Scaling tied to measurement/stopping rules | Pass | No scale claim; service split cap and revisit triggers |

## 7. Test, evidence, and teaching review

| Check | Result | Evidence/correction |
|---|---|---|
| Unit rules/edges | Pass | Plan test matrix and per-gate cases |
| Integration persistence/authorization/migrations | Pass | Real PostgreSQL/Keycloak requirements |
| Contract compatibility | Pass | Browser/BFF-Catalog/event contract gates |
| Gateway route/filter contracts | Pass | Startup manifest, method/path preservation, error, header-canary, and one-attempt tests |
| Deterministic concurrency/duplicates/lost response | Pass | Callback/profile/default/grant/deletion interleavings |
| Failure injection/recovery/reconciliation | Pass | Per-dependency and each deletion-phase gates |
| Security negatives and canaries | Pass | Actor/path/protocol/storage/log/trace/evidence matrices plus copied-session and outbound-header/token canaries |
| Representative performance environment | Conditional pass | Workload/targets preserved; topology manifest must be revised |
| Evidence identity/pass-fail | Pass | Build/config/dataset/environment/time/checksum/reviewer required |
| Driver/choice/benefit/cost/alternatives/revisit trigger taught | Pass | Decision records and teaching sections |
| Project-specific examples and evidence/uncertainty distinction | Pass | All artifacts use COM/CF examples and label assumptions |
| Learning points/questions/change triggers | Pass | HLD, LLD, schema, plan all end with these sections |

## 8. Approval path to implementation-ready

1. Product/architecture reviewers accept `IDA-DEC-001` and source synchronization is made explicit.
2. Technical/security reviewers accept `IDA-DEC-005` and its fail-closed route/filter boundaries.
3. Security reviewer accepts `IDA-DEC-003` or approves its replacement.
4. Local setup plan selects an official compatible Spring Boot/Cloud Gateway release train, verifies servlet/security/form-filter ordering, and selects build/dependency management, migrations, key/secret handling, runtime topology, and repeatable commands.
5. Product/technical owner approves the ordinary idempotency window before API freeze.
6. Security/privacy reviewer approves the deletion-ledger retention/restore arithmetic before COM-54.
7. E3 owns and links the versioned Cart deletion consumer before deletion completion is claimed.

After steps 1–4, COM-43/46 implementation can begin under the listed gates. Steps 5–7 are later bounded gates and do not require guessing during bootstrap.
