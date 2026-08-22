# Repository Guidelines

## Project Structure & Module Organization

This Java 25 Gradle monorepo contains Spring Boot services under `services/`: `identity-access-service` and `catalog-service`. Each owns `src/main/java`, resources and Flyway migrations under `src/main/resources`, tests in `src/test/java`, its build, container, and PostgreSQL database. Wire contracts live in `contracts/`; deployment assets in `deployment/`; checks in `scripts/`; guidance in `docs/`.

Do not add cross-service Gradle dependencies, shared domain models, cross-database joins, or shared migrations. Keep application classes in service root packages and organize by business capability, not global framework packages.

## Build, Test & Development Commands

- `./dev start` — create local configuration and start PostgreSQL, Keycloak, and both services.
- `./dev test` — run a clean Gradle `check`, including tests, contract checks, architecture boundaries, and secret scanning.
- `./dev verify` — check database isolation, readiness, APIs, and runtime canaries; services must be running.
- `./dev status` / `./dev stop` — inspect or stop the stack.
- `./gradlew :services:catalog-service:test` — run one service's tests directly when JDK 25 is available.

Use `./dev reset` only when intentionally deleting local database volumes.

## Coding Style & Naming Conventions
 Use four-space indentation, explicit imports, braces, constructor injection, immutable data where practical, and descriptive domain names. Types use `UpperCamelCase`, members `lowerCamelCase`, constants `UPPER_SNAKE_CASE`, and packages lowercase. Prefer cohesive code over tiny wrappers; extract methods for meaningful abstractions. Keep HTTP DTOs, domain objects, and JPA entities separate.

### Coding-standard instructions

For every implementation, refactoring, debugging, or code-review task:

1. Read `docs/development/java-spring-boot-coding-standards.md`
   before changing or reviewing Java, Spring, API, persistence,
   migration, configuration, or test code.
2. Treat MUST and MUST NOT rules as mandatory.
3. Follow SHOULD rules unless the change explains why another
   approach is clearer or safer.
4. Do not introduce an exception to the guide silently.
5. Before finishing an implementation:
    - review the final diff against the guide;
    - run the applicable formatter, static analysis, tests, and
      architecture checks;
    - report checks that were not run.
6. During code review:
    - report concrete violations with file and line references;
    - identify the violated section or principle;
    - distinguish required corrections from optional improvements;
    - do not report personal stylistic preferences as defects.

## Testing Guidelines

Tests use JUnit Jupiter and focused Spring test modules; PostgreSQL behavior uses Testcontainers. Name tests for scenario and expected outcome. Prefer unit or slice tests over `@SpringBootTest`. Cover relevant failure, authorization, concurrency, retry, and rollback behavior. Bug fixes require regression tests; no fixed coverage percentage replaces behavioral evidence.

## Commit & Pull Request Guidelines

History uses Conventional Commit subjects such as `feat(platform): ...`. Jira work uses `<type>(<scope>): COM-123 <imperative summary>`, branches like `feat/COM-123-short-description`, and PR titles like `COM-123: Add checkout validation`. PRs link Jira, summarize behavior, list validation, and note security, migration, or rollback concerns. Run `./dev test` and `./dev verify` before merge. refer [github-jira-workflow.md](docs/development/github-jira-workflow.md) more context, if only you need it.

## Security & Agent Instructions

Product-management and design work also follows `docs/product/AGENTS.md`. Never commit `.env`, credentials, tokens, PII-bearing logs, or build output. Agents must inspect applicable requirements and the coding standard before editing, preserve unrelated changes, review the final diff, and report checks not run. Reviews should cite concrete file/line violations and distinguish required fixes from optional improvements.
