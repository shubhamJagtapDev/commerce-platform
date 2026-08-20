# Java and Spring Boot Coding Standards

## 1. Purpose and scope

This guide defines the default coding and design standards for the Commerce Platform's Java 25 and Spring Boot services. It applies to production code, tests, database migrations, configuration, and code reviews.

The goal is not maximum uniformity or the largest possible rulebook. The goal is software that is correct, secure, observable, and easy to understand and change. When two solutions are equally correct, prefer the one that imposes less cognitive load on the next engineer.

This guide uses the following terms:

- **MUST / MUST NOT**: required for correctness, security, architectural integrity, or repository consistency. A deviation requires a documented architecture decision.
- **SHOULD / SHOULD NOT**: the normal choice. A pull request may deviate when it explains why the alternative is clearer or safer.
- **MAY**: context-dependent.

Automated formatting and static analysis settle mechanical questions. Human review focuses on behavior, boundaries, names, failure modes, and maintainability.

### 1.1 Industry baseline adopted by this project

There is no single universal "enterprise Java standard." This project combines standards by area and records local choices where the industry legitimately has alternatives.

| Area | Project baseline | How it applies |
|---|---|---|
| Java source format | Palantir Java Format, a Google Java Format-derived four-space style | Naming, imports, braces, whitespace, source layout, and Javadoc mechanics are automated where practical. |
| Spring application structure | Official Spring Boot and Spring Framework guidance | Root application packages, constructor injection, typed configuration, declarative local transactions, focused test slices, and production observability. |
| HTTP semantics | RFC 9110 | Methods, status codes, safety, idempotency, conditional behavior, and caching follow protocol semantics rather than local invention. |
| HTTP error format | RFC 9457 | APIs return stable `ProblemDetail` types through a centralized transport adapter. |
| API description | OpenAPI, pinned to the version used by repository contracts | Versioned contract files define request, response, security, and failure shapes; generated documentation does not replace contract review. |
| Event description | JSON Schema, pinned per versioned event contract | Producers and consumers validate compatible, immutable event versions. |
| Application security | OWASP ASVS and relevant OWASP Cheat Sheets | Security requirements and tests SHOULD reference applicable control IDs. ASVS Level 2 is the recommended default assurance target for authenticated business APIs, subject to an approved security decision. |
| Telemetry vocabulary | OpenTelemetry Semantic Conventions supported by the pinned instrumentation | Prefer standard HTTP, database, messaging, exception, service, and resource attributes. Pin convention versions and migrate deliberately when a convention is not yet stable. |
| Database evolution | Forward-only, versioned Flyway migrations | Applied migrations are immutable; constraints, compatibility, locks, backfills, rollout order, and verification are part of the change. |

These are baselines, not substitutes for judgment. "Clean Architecture," hexagonal architecture, SOLID, DRY, test pyramids, and microservices contain useful ideas but are not adopted as rigid compliance standards. Apply the underlying principle only when it reduces coupling or risk in the concrete design.

## 2. Core design philosophy

### 2.1 Minimize complexity

Complexity is anything that makes the system harder to understand or change. Watch for its three main symptoms:

- **Change amplification**: one conceptual change requires edits in many places.
- **Cognitive load**: understanding a behavior requires holding too many details or navigating too many layers.
- **Unknown unknowns**: it is unclear where a change belongs or what else it can affect.

Code SHOULD make the common path obvious and exceptional behavior explicit. A reader's first reasonable interpretation SHOULD be correct.

Do not optimize only for the smallest current patch. Spend proportionate effort simplifying the design for plausible future changes. Do not, however, build abstractions for hypothetical requirements that have no evidence.

### 2.2 Prefer deep modules

A good module offers a small, coherent interface and hides substantial implementation detail. Organize modules around the domain knowledge and decisions they own, not merely around workflow steps or framework stereotypes.

- Keep domain rules, persistence details, and invariants inside the owning module.
- Expose intention-revealing commands, queries, and domain events.
- Prefer a few cohesive entry points over many narrow pass-through services.
- Avoid layers whose only job is forwarding identical arguments and return values.
- Do not expose persistence entities as HTTP, event, or cross-module contracts.

Separate concerns when they change for different reasons or enforce different boundaries. Do not split code merely because it can be split; too many shallow classes and interfaces create indirection rather than modularity.

### 2.3 Hide information and eliminate special cases

Each design decision SHOULD have one clear owner. Callers should not need to know storage layout, retry mechanics, provider-specific identifiers, or internal state transitions unless that knowledge is part of the contract.

Prefer a uniform rule over accumulating branches for individual cases. When a special case is unavoidable, name the business reason, test it explicitly, and keep it close to the module that owns the rule.

### 2.4 Use general-purpose interfaces carefully

Prefer a simple reusable interface when multiple real use cases share the same semantics. Do not generalize merely to reduce duplicated lines.

An abstraction is justified when it:

- gives one name to a stable concept;
- hides meaningful complexity or volatility;
- protects an invariant or dependency direction; or
- removes a demonstrated family of special cases.

Avoid speculative extension points, single-implementation interfaces without a boundary purpose, generic `Base*` classes, and configuration switches for unapproved behavior.

## 3. Readability and method design

### 3.1 Optimize for local comprehension

Keep code that must be understood together close together. A somewhat longer cohesive method is preferable to a sequence of tiny methods that forces the reader to chase context.

Method length is a signal, not a rule. Extract a method when the extraction:

- names a meaningful operation or business concept;
- hides a complex or volatile detail;
- separates a distinct level of abstraction;
- removes distracting mechanics from the main flow; or
- enables focused reuse or testing that is valuable on its own.

Do not extract a method merely because it is a few lines long, because a style metric says so, or because it is called once or twice. Call count alone neither requires nor forbids extraction.

Avoid helpers that only rename a single obvious framework or collection call:

```java
// Avoid: the reader must navigate away but learns nothing new.
private boolean hasItems(List<LineItem> items) {
    return !items.isEmpty();
}
```

Extract when the name captures policy and the implementation hides relevant detail:

```java
private boolean exceedsCustomerPurchaseLimit(OrderDraft order, CustomerPolicy policy) {
    return order.total().compareTo(policy.maximumOrderValue()) > 0
            || order.itemCount() > policy.maximumItemsPerOrder();
}
```

### 3.2 Keep one level of abstraction in the main flow

Application methods SHOULD read as a coherent use case. Do not mix high-level policy decisions with low-level JSON parsing, SQL details, HTTP mechanics, or metric construction. Extract the lower-level detail when doing so gives it a clear boundary and name.

Use early returns or guard clauses to make invalid or exceptional paths explicit. Avoid deeply nested conditionals. Do not use clever expressions, nested ternaries, or long stream pipelines when straightforward control flow is easier to debug.

Streams are appropriate for clear collection transformations. Prefer a loop when the operation contains branching, mutation, checked failure handling, short-circuit rules that are not obvious, or needs step-by-step debugging.

### 3.3 Names and comments

Names MUST describe domain intent, not implementation trivia.

- Types and records: nouns or noun phrases, such as `Order`, `PaymentAttempt`, `InventoryReservation`.
- Methods: verbs or verb phrases, such as `reserveInventory`, `findActiveCart`, `canCancel`.
- Booleans: predicates, such as `expired`, `hasPermission`, `canTransitionToPaid`.
- Collections: plural nouns. Maps SHOULD reveal both sides when not obvious, such as `ordersByCustomerId`.
- Units belong in names when the type does not express them, such as `timeoutMillis`. Prefer `Duration`, `Instant`, and domain value types so unit suffixes are unnecessary.

Avoid vague names such as `data`, `info`, `item`, `object`, `manager`, `helper`, `util`, `process`, and `handle` unless the domain genuinely uses that term. Do not encode type in a name (`orderList`, `strName`) or use unexplained abbreviations.

Comments and Javadoc explain intent, constraints, rationale, surprising behavior, or external obligations. They MUST NOT restate the code. Delete or update comments in the same change that invalidates them. Public or cross-module contracts SHOULD document semantics, failure behavior, idempotency, and units when those are not fully expressed by types.

### 3.4 Make invalid states difficult to represent

Use strong domain types for identifiers, money, quantities, states, and constrained values when they prevent accidental mixing or centralize meaningful rules. Prefer records for immutable data carriers and value objects when their semantics fit.

- Prefer immutable state and defensive copies at boundaries.
- Use `enum` or a sealed hierarchy for a closed set of states; do not use free-form strings.
- Use `BigDecimal` for monetary decimal values and define scale, rounding, and currency behavior explicitly.
- Use `Instant` for timestamps and `Clock` for testable current time. Do not call the system clock throughout domain code.
- Return empty collections rather than `null` collections.
- Do not use `Optional` for fields, parameters, DTO properties, or JPA entity attributes. It MAY be used as a return type when absence is normal and the caller must decide what it means.
- Never call `Optional.get()` without a preceding proof of presence; prefer `orElseThrow`, `map`, or explicit branching.
- In `com.commerce` source, values are non-null by default under NullAway. Use `org.jspecify.annotations.Nullable` at the type use when an API intentionally permits absence, and handle that absence at the owning boundary.

## 4. Java source conventions

Mechanical Java formatting MUST follow the repository's Palantir Java Format configuration. It provides a consistent, Google Java Format-derived style while preserving this project's four-space indentation requirement.

- Source files use UTF-8, spaces rather than tabs, and one top-level type per file.
- Package names are lowercase. Types use `UpperCamelCase`; methods, parameters, and variables use `lowerCamelCase`; constants use `UPPER_SNAKE_CASE`.
- Imports MUST be explicit; wildcard imports and unused imports are forbidden.
- Braces are required for `if`, `else`, loops, and try blocks, including single-line bodies.
- Prefer `var` only when the initializer makes the exact type obvious and the name carries the semantics. Do not use it when the type communicates domain meaning or API behavior.
- Use constructor injection. Required dependencies SHOULD be `private final` fields. Field injection is forbidden.
- Do not use `final` on every local variable or parameter. Use it when it protects an important capture or reassignment constraint.
- Do not catch `Exception` or `Throwable` except at a deliberate process or transport boundary that handles it safely.
- Do not silently swallow exceptions.
- Do not use finalizers.
- Suppress a warning at the narrowest possible location and explain a non-obvious suppression.

Prefer standard library and Spring capabilities over local utilities. A new dependency MUST solve a demonstrated need, have acceptable maintenance and security characteristics, and be approved through normal dependency review.

## 5. Service and package structure

Each deployable service keeps its application class in the root package, for example `com.commerce.catalog`, so component and entity scanning remain bounded.

Within a service, package primarily by business capability, then by internal concern when needed:

```text
com.commerce.catalog
├── CatalogApplication.java
├── product
│   ├── Product.java
│   ├── ProductApplicationService.java
│   ├── ProductRepository.java
│   ├── ProductHttpController.java
│   └── ProductJpaAdapter.java
├── category
└── config
```

Do not create one service-wide `controller`, `service`, `repository`, `entity`, or `dto` package that mixes unrelated capabilities.

The normal dependency direction is:

```text
transport / scheduled jobs -> application use cases -> domain
                                      |
                                      v
                           outbound ports or owned persistence
```

- Domain code MUST NOT depend on HTTP, controllers, serialization, or persistence adapters.
- Controllers translate transport input and output; they do not own business rules or transactions.
- Application services coordinate a use case, authorization, transaction, domain objects, and outbound dependencies.
- Repositories provide persistence operations meaningful to the owning domain. Do not expose a generic persistence API merely because Spring Data makes it easy.
- Do not add an interface for every class. Add one at a real module, provider, testing, or dependency-inversion boundary.
- Package-private visibility is preferred for implementation details. Make a type `public` only when another package is intended to depend on it.

Cross-service Gradle project dependencies, shared domain-model libraries, cross-database joins, and shared migrations are forbidden by the repository architecture.

## 6. Spring usage

### 6.1 Dependency injection and configuration

- Use constructor injection exclusively for required collaborators.
- Prefer one obvious bean for a role. When multiple beans are intentional, name and qualify them explicitly.
- Keep `@Configuration` classes focused. Use `@Configuration(proxyBeanMethods = false)` when inter-bean method proxying is not required.
- Bind related external configuration to validated, immutable `@ConfigurationProperties` types. Prefer this to scattered `@Value` expressions.
- Secrets MUST come from approved runtime secret sources and MUST NOT be committed, logged, or returned by actuator endpoints.
- Configuration defaults MUST be safe. Production behavior must not depend on a developer profile being active.
- Do not use the Spring application context as a service locator.

### 6.2 Stereotypes and proxies

Use Spring stereotypes to communicate runtime roles, not as decoration. Be aware that proxy-based features such as `@Transactional`, caching, retry, and method security may not apply to self-invocation or non-proxied calls.

Avoid hidden control flow from stacked annotations. When behavior depends on proxy ordering or subtle propagation, make the design explicit and test it at the Spring boundary.

### 6.3 Transactions

Transaction boundaries belong at application use-case methods, not controllers. A transaction SHOULD protect one local consistency boundary and be as short as correctness permits.

- Use declarative `@Transactional` for normal local database transactions.
- Mark query-only use cases `@Transactional(readOnly = true)` when they require a consistent persistence context or benefit from read-only optimization. Do not annotate trivial calls mechanically.
- Do not make remote HTTP calls, publish non-transactional messages, wait on user input, or perform long-running work inside a database transaction.
- State non-default propagation or isolation explicitly and explain the invariant that requires it.
- Remember that Spring rolls back by default for unchecked exceptions, not all checked exceptions. Define rollback behavior deliberately when checked exceptions cross the boundary.
- Design concurrent writes explicitly with database constraints, optimistic locking, pessimistic locking, or atomic SQL as appropriate. A prior read followed by a write is not concurrency control.
- Use a transactional outbox when a committed database change and an external event must not diverge. Do not claim atomicity between a database transaction and a broker publish without a mechanism that provides it.

### 6.4 Persistence and JPA

JPA entities are persistence models owned by one service and module. They MUST NOT be API responses, event payloads, or cross-module contracts.

- Define table and column names explicitly when relying on an implicit naming strategy would make migrations or interoperability unclear.
- Model nullability in both Java validation/domain rules and database constraints.
- Prefer explicit fetching for known use cases. Avoid `EAGER` associations as a general fix and detect N+1 query behavior in integration tests or query evidence.
- Keep aggregate loading bounded. Do not map large or unbounded collections as convenient entity graphs.
- Do not use cascade operations or orphan removal without understanding and testing their deletion and update effects.
- Implement entity equality deliberately. Do not include mutable associations or generated identifiers in equality in ways that break before persistence.
- Do not put network calls, repositories, logging, or framework lookups in entity methods.
- Repository methods SHOULD express a domain query or command. Avoid leaking `Pageable`, JPA specifications, or persistence-specific types across a module boundary unless that is the intentional contract.
- Use database constraints as the final enforcement layer for local uniqueness, referential integrity, and valid persisted shapes.

Every schema change MUST use a new immutable Flyway migration. Never edit a migration that may have run outside an ephemeral local environment. Migrations SHOULD be forward-compatible with rolling deployment, bounded in lock duration, reversible through a documented forward fix, and tested against PostgreSQL.

## 7. HTTP API standards

The versioned OpenAPI files under `contracts/openapi/` are the authoritative wire contracts. Implementation MUST not drift from them.

- Controllers remain thin: validate and translate input, invoke one application use case, and translate the result.
- Use request and response DTOs distinct from domain objects and persistence entities.
- Use Bean Validation for structural boundary checks. Enforce business invariants in the owning domain or application module as well; annotations alone are not a domain model.
- Reject unknown or malformed values according to the contract. Do not silently coerce ambiguous input.
- Use correct HTTP semantics and status codes. Do not return `200 OK` with an error envelope.
- Use one global `@RestControllerAdvice` per service to map known application failures to stable RFC 9457 `ProblemDetail` responses.
- Error responses MUST be safe for clients: no stack traces, SQL, secret values, provider internals, or sensitive personal data.
- Preserve a stable machine-readable problem `type`; human-readable text may evolve without becoming client control flow.
- Authorization MUST be enforced for every protected operation at the owning boundary. Hiding a route or UI control is not authorization.
- Idempotency semantics MUST be explicit for commands that clients or infrastructure can retry. A correlation ID is not an idempotency key.
- Define and enforce request size, pagination, sorting, and timeout bounds. Do not expose unbounded collection endpoints.

## 8. Errors and exception handling

Exceptions communicate exceptional failure, not ordinary branching.

- Use domain- or application-specific exception types when callers or transport adapters need to distinguish failure semantics.
- Do not throw `IllegalArgumentException` for every business failure; use it for programmer or direct argument contract violations.
- Preserve the cause when translating infrastructure exceptions.
- Translate exceptions once at the appropriate boundary. Avoid catch-log-rethrow patterns that duplicate logs without adding recovery or context.
- Do not expose vendor exceptions across module or HTTP boundaries.
- Log unexpected failures once at the boundary responsible for the outcome. Expected client or domain failures usually do not need error-level stack traces.
- Recovery MUST be explicit. If an exception is ignored because the operation is best-effort, document the accepted loss, emit suitable evidence, and test the behavior.

## 9. Security and privacy

Security rules are correctness rules and are not optional cleanup.

- Deny by default and permit only named routes and operations.
- Validate authorization using authoritative identity and resource ownership, not request-supplied identity fields.
- Validate input by type, length, range, format, and allowed values. Apply request-size limits before expensive processing.
- Use parameterized persistence APIs. Never concatenate untrusted input into SQL, JPQL, shell commands, paths, or log formats.
- Use allowlists for redirect targets, sortable fields, file types, and algorithms where applicable.
- Never log access tokens, refresh tokens, passwords, session cookies, API keys, raw payment data, or unnecessary PII.
- Apply data minimization to DTOs, events, logs, metrics, and traces.
- Do not weaken TLS validation, certificate checks, CSRF protection, CORS, or security headers without an approved and documented threat-model decision.
- Management and diagnostic endpoints MUST expose the minimum necessary data and follow the deployment's access-control policy.
- Every security-sensitive change requires negative tests proving unauthorized and cross-owner access is rejected.

## 10. Logging, metrics, and tracing

Observability should answer what happened, where, for whom (safely), and with what outcome—without requiring a code change or exposing sensitive data.

- Use structured, parameterized logging; do not build log messages with string concatenation.
- Use consistent event names and stable fields. Include correlation or trace identifiers through the configured observability stack rather than manually threading them everywhere.
- Choose levels consistently: `ERROR` for failures requiring attention, `WARN` for degraded or suspicious conditions, `INFO` for meaningful lifecycle/business outcomes, and `DEBUG` for diagnostic detail.
- Do not log the same failure at every layer.
- Metrics names and low-cardinality labels MUST be stable. Never use customer IDs, order IDs, URLs with identifiers, exception messages, or other unbounded values as metric labels.
- Instrument meaningful boundaries and outcomes. Avoid duplicate observations around operations already instrumented by Spring.
- Logs and metrics are not substitutes for audit records. Security- or business-critical audit evidence needs explicit ownership, integrity, access, and retention rules.

## 11. Resilience and asynchronous work

Do not add retries, circuit breakers, caches, queues, or asynchronous execution by habit. Each mechanism requires a named failure mode and operational behavior.

- Every remote call MUST have a bounded deadline. Timeouts include connection and response behavior as appropriate.
- Retries require an idempotent operation or deduplication, a bounded attempt count, backoff with jitter, and one clear retry owner.
- Never nest retries across layers without a deliberate total-attempt budget.
- A circuit breaker requires a safe open-state response and evidence that it prevents cascading failure.
- Asynchronous messages require stable versioned contracts, idempotent consumers, durable retry or dead-letter handling, observability, and reconciliation for lost or poison work.
- Thread pools and queues MUST be bounded and named. Define saturation and rejection behavior.
- Do not use `@Async` to hide latency or evade a missing reliability design.

## 12. Testing standards

Tests provide evidence for behavior and risk; coverage percentage alone is not the goal.

### 12.1 Test at the smallest useful scope

- **Unit tests** cover domain rules, state transitions, value objects, and non-trivial algorithms without starting Spring.
- **Slice tests** cover MVC, JSON, security, validation, or persistence adapters with only the relevant Spring facilities.
- **Integration tests** cover transactions, migrations, PostgreSQL behavior, security chains, serialization, and wiring. Use Testcontainers when database behavior matters; do not substitute an incompatible in-memory database.
- **Contract tests** verify implementation compatibility with versioned OpenAPI and event schemas.
- **End-to-end tests** are reserved for a small set of critical cross-process flows and failure paths.

Do not use `@SpringBootTest` for logic that can be tested without the full application context.

### 12.2 Test behavior, not implementation trivia

- Use descriptive names that state the scenario and expected outcome.
- Follow arrange/act/assert structure where it improves readability; do not add comments that merely label obvious blocks.
- Assert observable outcomes, persisted state, emitted contracts, and important collaborator interactions—not private method calls or incidental ordering.
- Cover success, boundary values, invalid input, authorization denial, concurrency, duplicate/retry behavior, dependency failure, and rollback when relevant.
- Tests MUST be deterministic and independent. Control time with `Clock`; do not use sleeps, real external services, execution order, or shared mutable fixtures.
- A production bug fix MUST include a regression test that fails for the original defect.
- Test data builders or fixtures SHOULD expose meaningful defaults and make scenario-specific differences obvious. Avoid giant shared fixtures whose irrelevant fields obscure the test.
- Mocks are appropriate at owned boundaries. Excessive mocking that mirrors every internal call signals excessive coupling or tests tied to implementation.

## 13. Review and change standards

Keep pull requests cohesive and reviewable. Do not combine behavior changes, broad refactoring, dependency upgrades, and mechanical formatting unless they are inseparable.

Before approval, reviewers verify:

### Design and readability

- The change has one clear purpose and belongs to the correct service and domain owner.
- The simplest design that preserves correctness was selected.
- Names reveal intent, the main path is obvious, and comments explain rationale rather than syntax.
- Methods and classes are cohesive without being fragmented into shallow wrappers.
- New abstractions hide real complexity and are supported by current use cases.
- The change does not create special cases, duplicated policy, or change amplification.

### Spring and data correctness

- DTOs, domain objects, and persistence entities remain separate.
- Validation, authorization, transaction, and exception boundaries are explicit.
- Concurrency, retries, idempotency, time, and failure behavior are handled where applicable.
- Queries have bounded access paths; new indexes name the query they support and acknowledge write/storage cost.
- Flyway migrations are immutable, safe for deployment order, and verified on PostgreSQL.

### Security and operations

- The change denies unauthorized behavior and has negative security tests.
- No secrets or sensitive data enter source, logs, errors, metrics, traces, or events.
- Timeouts, resource bounds, observability, and recovery evidence are appropriate to the risk.
- Configuration is typed, validated, and safe by default.

### Verification

- Tests cover behavior and meaningful failure paths at the smallest useful scope.
- API and event implementations match versioned contracts.
- `./dev test` and `./dev verify` pass, or the pull request documents an actionable blocker.
- Any intentional deviation from a **MUST** rule links to the approving decision.

## 14. Automated enforcement

The repository enforces mechanical rules locally and in CI so reviewers do not debate them repeatedly. `./gradlew check` is the hard quality gate and aggregates every service's `check` lifecycle as well as the repository's architecture, contract, and secret checks. The CI workflow runs this same command before it starts the integration environment.

| Check | Enforcement | Developer command |
|---|---|---|
| Java format | Spotless with Palantir Java Format | `./gradlew spotlessApply` fixes files; `./gradlew check` rejects unformatted files. |
| Static analysis | Error Prone during Java compilation | `./gradlew check` rejects Error Prone errors and explicitly promoted warnings. |
| Null safety | NullAway during production and test compilation; JSpecify annotations express intentional absence | `./gradlew check` rejects unsafe nullable use in `com.commerce` packages. |
| Architecture | ArchUnit tests per service plus monorepo boundary scripts | `./gradlew check` rejects forbidden framework and repository dependencies. |
| Existing quality gates | Tests, contracts, secret scanning, and service-boundary checks | `./dev test` runs a clean `check`; `./dev verify` adds running-system checks. |

Keep quality gates focused and deterministic. Suppressions are narrow, justified, and reviewed. New static-analysis checks begin as warnings or receive a deliberate rollout plan when they would produce widespread false positives.

Tools enforce syntax and known hazards. They MUST NOT impose arbitrary method-length, class-count, interface-per-class, or coverage-percentage targets that encourage shallow abstractions or tests without behavioral value.

## 15. Sources and project authority

This guide adapts established Java, Spring, HTTP, and security practices to this repository. The project remains the authority for its architecture and wire contracts.

- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- [Spring Boot: Structuring Your Code](https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html)
- [Spring Boot: Testing](https://docs.spring.io/spring-boot/reference/testing/index.html)
- [Spring Boot: Observability](https://docs.spring.io/spring-boot/reference/actuator/observability.html)
- [Spring Framework: Transaction Management](https://docs.spring.io/spring-framework/reference/data-access/transaction.html)
- [Spring Framework: JPA](https://docs.spring.io/spring-framework/reference/data-access/orm/jpa.html)
- [RFC 9110: HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110)
- [RFC 9457: Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457)
- [OpenAPI Specification](https://spec.openapis.org/oas/latest.html)
- [OpenTelemetry Semantic Conventions](https://opentelemetry.io/docs/specs/semconv/)
- [OWASP Application Security Verification Standard](https://owasp.org/www-project-application-security-verification-standard/)
- [OWASP REST Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html)

When this guide conflicts with an approved product specification, architecture decision, versioned contract, or a stricter security requirement, the more authoritative or stricter rule wins. Update this guide when a repeated, evidence-backed project convention changes; do not silently establish competing conventions in individual pull requests.
