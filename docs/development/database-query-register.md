# Database Query Register

> **Policy:** Required for every use case that reads or writes an application database.

| Use case | Service/owner | Transaction | JPA operation | Equivalent raw SQL | Cardinality/index | Lock/concurrency | Evidence trigger |
|---|---|---|---|---|---|---|---|
| Readiness database probe | Identity Access / platform | Driver-managed read-only probe | Actuator datasource health indicator | `SELECT 1` | One row; no application index | No application lock | Fail readiness when `identity_access` is unavailable |
| Readiness database probe | Catalog / platform | Driver-managed read-only probe | Actuator datasource health indicator | `SELECT 1` | One row; no application index | No application lock | Fail readiness when `catalog` is unavailable |

## Entry template

| Use case | Service/owner | Transaction | JPA operation | Equivalent raw SQL | Cardinality/index | Lock/concurrency | Evidence trigger |
|---|---|---|---|---|---|---|---|
| `ID/name` | `service/module` | `read-only/read-write and commit boundary` | `Repository method/specification` | `SELECT/INSERT/UPDATE ...` | `Expected rows and supporting index` | `Optimistic/pessimistic/advisory/none` | `Dataset and EXPLAIN/fault/concurrency condition` |
