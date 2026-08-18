# Gate 0 — Topology Decision and Source Synchronization

> **Status:** Complete  
> **Accepted:** 2026-08-17  
> **Scope:** `IDA-DEC-001`–`005`, `CF-PERF-DEC-001`, `CF-XCAP-SCN-022`–`028`

## Accepted decision

Use a monorepo containing two independently deployable application microservices:

1. Identity Access Service owns the same-origin BFF, customer and maintainer login/logout, customer account/profile/address/deletion, and the stateless embedded Gateway Server Web MVC adapter.
2. Catalog Service owns catalog data and the final catalog-maintainer grant decision.
3. Keycloak is version-pinned external identity infrastructure, not a custom third application service.

The monorepo centralizes the Java toolchain, dependency platform, quality conventions, contract catalogue, local orchestration, and CI entrypoints. It does not create a shared business runtime: services have no cross-project source dependency, shared domain model, shared migration path, cross-database grant, or coordinated release requirement.

## Supersession classification

The accepted decision supersedes only the previous modular-monolith/one-Spring-process topology. It preserves:

- approved product behavior and actor boundaries;
- the security, privacy, denial, session, and deletion requirements;
- PostgreSQL-first storage/search policy and the ban on speculative cache/search/replica infrastructure;
- the original combined application resource envelope;
- `D2`, workload, latency, throughput, correctness, repetition, stopping, and evidence rules; and
- the Week 1/P0 and Week 5/6 timeboxes.

`CF-PERF-DEC-001` now names one instance of each application service on the unchanged combined envelope. `CF-XCAP-SCN-022`–`028` use that exact environment and must record per-service plus combined resource evidence. Old one-process results cannot be relabelled as results for the amended scenarios.

## Security disposition

`IDA-DEC-003` is accepted with a narrow boundary: only the server-held catalog-maintainer access token may cross the private Identity Access-to-Catalog hop. Catalog validates the full token and checks its own active grant in the write transaction. Customer tokens, refresh/identity tokens, browser cookies, CSRF values, and caller identity/role/owner headers never cross. COM-46 still cannot pass without the negative token/header canary matrix and fail-closed evidence.

`IDA-DEC-005` is accepted with explicit Java routes only, startup validation, no catch-all, no route store, no Redis/Spring Session/default authorized-client store, no unsafe retry, and Catalog final authorization. Its ordering and sanitation claims remain evidence to produce, not Gate 0 claims.

## Source-sync record

- Repository performance baseline amended without reducing targets.
- Repository cross-capability acceptance environment amended for scenarios `022`–`028`.
- Notion Decisions/Rationale, performance baseline, and acceptance baseline synchronized on 2026-08-17.
- Architecture decisions `IDA-DEC-001`–`005` marked accepted; later implementation gates still own their executable evidence.

## Gate result

Gate 0 passes. No valid new performance claim may use the obsolete one-process environment, and no acceptance statement implies that Gate 1 or later behavior has already passed.
