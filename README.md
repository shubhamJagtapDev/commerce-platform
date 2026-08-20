# Commerce Platform

This is a Java 25 monorepo with independently deployable Identity Access and Catalog microservices. Keycloak is external identity infrastructure. Each application service owns its source, build, migrations, runtime configuration, container, and logical PostgreSQL database.

Start with the [Master Local Setup and Decision Guide](docs/development/master-local-setup-guide.md) for architecture ownership, prerequisites, commands, ports, API versioning, Swagger policy, validation, troubleshooting, and the decisions behind the baseline.

Use the [GitHub + Jira Development Workflow](docs/development/github-jira-workflow.md) when creating branches, commits, and pull requests for Jira work items.

Follow the [Java and Spring Boot Coding Standards](docs/development/java-spring-boot-coding-standards.md) for design, implementation, testing, and code review.

## Local path

Prerequisites: Docker Desktop/Engine with Compose and OpenSSL. A host JDK is optional because the repository pins a Gradle Java 25 container fallback.

```bash
./dev start
./dev test
./dev verify
./dev stop
```

Local endpoints:

- Identity Access readiness: `http://localhost:8080/actuator/health/readiness`
- Identity Access Swagger UI: `http://localhost:8080/swagger-ui.html`
- Catalog readiness: `http://localhost:8081/actuator/health/readiness`
- Catalog Swagger UI: `http://localhost:8081/swagger-ui.html`
- Keycloak: `http://localhost:8082`
- PostgreSQL (optional host access): `localhost:55432`

`./dev start` creates a mode-600, git-ignored `.env`; no passwords, client secrets, tokens, or cookies belong in source control or evidence. Use `./dev reset` only when you intentionally want to remove local database volumes.

## Monorepo rules

- Centralize toolchain/dependency versions, build conventions, contracts, CI, and local orchestration.
- Do not create cross-service Gradle project dependencies or a shared domain-model library.
- Do not join across service databases, share migrations, or grant one application role access to another database.
- Services must build, test, migrate, deploy, roll back, and become ready independently.
- Share only stable wire contracts and non-business build/test conventions.
- Adding a service requires an accepted boundary decision, named owner, independent data authority, SLO/failure analysis, and CI/runtime budget.
