# IntelliJ IDEA Run Configurations

Use these run configurations when you want readable service logs and debugger support from IntelliJ IDEA while keeping shared local infrastructure in Docker.

## What Runs Where

| Process | Runtime |
|---|---|
| PostgreSQL | Docker Compose |
| Keycloak | Docker Compose |
| Keycloak fixtures | Docker Compose one-shot job |
| Identity Access Service | IntelliJ IDEA |
| Catalog Service | IntelliJ IDEA |

The IDE profiles use `SPRING_PROFILES_ACTIVE=dev,idea`. The `idea` profile imports the ignored local `.env` file and rewrites service-to-container hostnames to localhost endpoints.

IDE service runs use the same colored human-readable console log format as the Docker `dev` profile. Staging and production keep ECS JSON stdout for log ingestion.

## First-Time Setup

From the repository root:

```bash
scripts/init-local-env.sh
docker compose --env-file .env -f deployment/local/compose.yaml up --build -d postgres keycloak keycloak-fixtures
```

Then open the repository in IntelliJ IDEA and use one of the shared run configurations:

- `Identity Access Service (local IDE)`
- `Catalog Service (local IDE)`
- `Commerce Services (local IDE)`

The compound `Commerce Services (local IDE)` configuration starts both Spring Boot services.

## Ports

| Endpoint | URL |
|---|---|
| Identity Access | `http://localhost:8080` |
| Identity Swagger UI | `http://localhost:8080/swagger-ui.html` |
| Catalog | `http://localhost:8081` |
| Catalog Swagger UI | `http://localhost:8081/swagger-ui.html` |
| Keycloak | `http://localhost:8082` |
| PostgreSQL | `localhost:55432` |

## Avoiding Port Conflicts

Do not run the Spring service containers and the IntelliJ service processes at the same time because they bind the same ports. If you previously ran the full local stack, stop only the service containers:

```bash
docker compose --env-file .env -f deployment/local/compose.yaml stop identity-access-service catalog-service
```

Keep `postgres`, `keycloak`, and the completed `keycloak-fixtures` setup available.

## Validation

After starting the services from IDEA:

```bash
curl --fail --silent http://localhost:8080/actuator/health/readiness
curl --fail --silent http://localhost:8081/actuator/health/readiness
curl --fail --silent http://localhost:8080/api/v1/foundation
curl --fail --silent http://localhost:8081/api/v1/foundation
```

Full `./dev verify` is primarily for the Docker-run stack. For IDE-run services, use the health/API checks above and `./dev test` before committing.

## Secret Handling

The committed IntelliJ run configurations do not contain passwords, client secrets, tokens, cookies, or generated keys. They only activate profiles. Runtime secrets stay in the generated `.env` file, which remains ignored by Git.
