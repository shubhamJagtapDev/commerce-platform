# Gate 1 — Repository and Local-Runtime Bootstrap Evidence

> **Result:** PASS  
> **Validated:** 2026-08-17  
> **Scope:** Foundation for COM-43; no customer or maintainer authentication behavior is claimed yet

## Implemented baseline

- Java 25, Gradle 9.6.1, Spring Boot 4.1.0, Spring Cloud 2025.1.2, Gateway Server Web MVC 5.0.2, PostgreSQL 18.4, Keycloak 26.7.0, and Springdoc 3.0.3 are centrally pinned.
- Identity Access and Catalog are independently buildable and deployable Gradle projects with separate containers, migrations, configuration, tests, and PostgreSQL databases/application roles.
- Identity Access embeds a stateless Gateway MVC route-registry foundation. Discovery, catch-all/ambiguous routes, gateway persistence, retries, circuit breakers, Redis, Spring Session, and a second token store are rejected by configuration, startup validation, dependency locks, or architecture checks.
- Both services use Spring Framework native path API versioning. `/api/v1/foundation` resolves to controller version `1.0`; unsupported `/api/v2/foundation` returns `400`.
- Both dev profiles expose annotated Springdoc OpenAPI and Swagger UI. Staging and production profiles disable runtime API docs/UI; checked-in OpenAPI remains the reviewed contract source.
- Local Keycloak imports a versioned realm/client definition. PostgreSQL creates independently owned `identity_access`, `catalog`, and `keycloak` databases with no cross-application grants.
- Actuator readiness includes each service's owned database; liveness does not. JSON stdout, Micrometer/Prometheus, and OpenTelemetry foundations are present without custom logging AOP.

## Executed evidence

`./dev test` and `./dev verify` passed on the complete Docker Compose topology. The verification proves:

1. unit tests and route-registry negative tests pass;
2. monorepo dependency/migration boundaries and versioned contract schemas pass;
3. committed-secret heuristic and Git-ignore policy pass;
4. both readiness endpoints, OpenAPI documents, Swagger UIs, and Keycloak discovery are reachable;
5. both version-1 foundation endpoints succeed and unsupported version 2 fails;
6. the runtime gateway manifest contains zero routes;
7. Identity Access credentials cannot connect to Catalog and Catalog credentials cannot connect to Identity Access;
8. a request-secret canary does not appear in service logs; and
9. stopping PostgreSQL leaves liveness at `200`, moves readiness to `503`, and restoring PostgreSQL returns readiness to `200`.

`./dev digest` emits sorted SHA-256 inputs plus locked platform, container image, Docker environment, and runtime route-manifest identifiers. It excludes `.env`, generated evidence, and build output.

## Scope boundary and next gate

The foundation endpoints are dev-only diagnostics, not product APIs. Gate 1 does not claim login, logout, customer registration, server-side sessions, CSRF protection, or Catalog maintainer authorization. Those begin at Gate 2 with a real Keycloak Authorization Code + PKCE principal boundary and its security-negative evidence matrix.
