# Local Development Setup — Gate 1

> **Status:** Implemented and locally validated  
> **Date:** 2026-08-17

For the complete developer handoff, decision register, troubleshooting guide, and extension rules, use [`master-local-setup-guide.md`](master-local-setup-guide.md).

## Locked platform

| Concern | Selection | Reason |
|---|---|---|
| Language/runtime | Java 25 | Requested baseline; use language advancements only when they simplify measured code. Virtual threads are enabled for the servlet/JDBC workload. |
| Build | Gradle Wrapper 9.6.1, Kotlin DSL | Java 25-compatible, reproducible entrypoint; root orchestration with independent service subprojects. |
| Application | Spring Boot 4.1.0 | Current stable Boot line supporting Java 25. |
| Cloud/gateway | Spring Cloud 2025.1.2; Gateway Server Web MVC 5.0.2 via BOM | Official Boot 4.1-compatible release train; preserves servlet/JDBC execution. |
| Persistence | Spring Data JPA + Hibernate; Flyway; PostgreSQL 18.4 | Product/setup decision. Hibernate validates but never creates production schema. |
| Identity | Keycloak 26.7.0 | Version-pinned external OIDC provider with versioned realm/client import. |
| API docs | Springdoc OpenAPI 3.0.3 + Swagger annotations/UI | Boot 4 line. Runtime UI is dev-only; checked-in OpenAPI remains the reviewed contract. |
| Observability | Actuator, Micrometer, Prometheus registry, Boot OpenTelemetry starter, dev-friendly local console logs, ECS JSON stdout in staging/production | Standards-first instrumentation without a custom AOP telemetry layer or vendor lock-in. |

## Configuration policy

- YAML only: `application.yml` plus `application-dev.yml`, `application-stg.yml`, and `application-prd.yml` per service.
- The base configuration contains no secret default. Credentials arrive through environment variables or a later approved secret manager.
- `dev` enables Swagger UI, 100% trace sampling, and colored human-readable console logs. `idea` keeps the same local log format for IDE runs. `stg`/`prd` disable Swagger UI, use bounded configurable sampling, and emit ECS JSON stdout.
- Do not add Spring Cloud Config yet: it would add an operational service before configuration scale or dynamic-refresh evidence justifies it.
- `./dev start` generates `.env` once with OpenSSL, mode 600. Rotate locally by deleting `.env` and intentionally resetting the local volumes.

## API and exception policy

Use Swagger `@Operation`, `@ApiResponse`, `@Parameter`, and DTO `@Schema` annotations on controller contracts. Generated `/v3/api-docs` is checked against the reviewed `contracts/openapi` source when business endpoints arrive. Swagger UI's “try it out” is disabled at foundation stage.

Use Spring Framework 7 native MVC API versioning, not a custom interceptor. The version is the second path segment for paths beginning `/api/v` (`/api/v1/...`), parsed as semantic version `1.0`; supported versions are explicitly configured and unknown versions fail with `400`. Annotated controllers declare `@RequestMapping(version = "1.0")` or an explicitly reviewed baseline mapping. Actuator, Swagger UI, and `/v3/api-docs` are outside the version-path predicate.

Use one global `@RestControllerAdvice` per service for stable RFC 9457 `ProblemDetail` transport shapes. A module may translate its own exception into a semantic application error before the global adapter; do not scatter HTTP response construction through domain code.

Use servlet filters for correlation/trusted-proxy/request-boundary concerns and Spring Security filters for authentication/CSRF. Use MVC interceptors only when handler metadata is required. Use Gateway filters only for explicit downstream routing policy. Do not add AOP for logging or generic timing; use Micrometer Observation and standard framework instrumentation.

## Database and JPA rule

Every database-touching use case must add a row to [`database-query-register.md`](database-query-register.md) before implementation: purpose, owner, transaction boundary, JPA repository operation, equivalent raw SQL, expected cardinality, index, lock/concurrency behavior, and `EXPLAIN` evidence trigger. Raw SQL documents the access path; application code still uses JPA unless a reviewed measured exception is recorded.

Readiness includes the owned database indicator; liveness does not. Each service has its own Flyway location and database role. The local physical PostgreSQL server is shared only to reduce development cost.

Local JDBC URLs pin PostgreSQL driver connect/socket timeouts to two seconds so a half-open pooled connection yields a bounded `503` readiness response. Staging/production URLs must carry reviewed values aligned with their probe timeout and failure budget; leaving the driver socket timeout unbounded is not permitted.

## Commands and evidence

| Command | Result |
|---|---|
| `./dev start` | Generates local secrets and builds/starts PostgreSQL, Keycloak, Identity Access, and Catalog. |
| `./dev test` | Runs unit, architecture, contract, and secret checks using host Java 25 or the pinned Gradle container. |
| `./dev verify` | Re-runs checks; verifies Swagger, native API versioning, Keycloak discovery, gateway invariants, telemetry canaries, readiness failure behavior, and cross-database denial. |
| `./dev digest` | Prints reproducible SHA-256 build/config/contract/route-foundation inputs without secrets. |
| `./dev stop` | Stops containers and preserves local data. |
| `./dev reset` | Intentionally removes local containers and database volume; local-only destructive operation. |

The current workstation has Java 24.0.2, so the command path uses the pinned Gradle Java 25 container fallback. Gate 1 was validated against the complete local topology on 2026-08-17; see [`gate-1-bootstrap-evidence.md`](../architecture/customer-identity-access/gate-1-bootstrap-evidence.md).
