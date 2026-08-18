# IDA-DEC-004: Accept customer deletion at a local deny boundary, then reconcile

> **Status:** Accepted — Gate 0 owner review, 2026-08-17  
> **Date:** 2026-08-13  
> **Owners/reviewers:** Technical owner, security/privacy reviewer, E3 Cart owner  
> **Controlling requirements:** `CF-ACC-002`, `CF-SEC-SES-007`–`008`, `CF-SEC-NFR-009`–`010`, `CF-SEC-PII-007`, COM-16/COM-52/COM-54  
> **Supersedes/superseded by:** None

## Decision

Accept account deletion only after one Identity Access database transaction changes the account to a permanently denying state, increments its security epoch, removes/revokes local sessions and active customer PII, and records durable workflow, deletion-ledger, outbox, audit, and idempotency state. Return `202` after that commit; reconcile Keycloak and the future Cart Service with idempotent, retryable phases.

No remote failure may restore local access. Completion is not reported until every mandatory owner acknowledges cleanup, and restored backups are gated by the deletion ledger before traffic.

## Context and design pressure

- Keycloak owns credential/identity state; Identity Access owns account/profile/address/session state; the later Cart Service owns account cart state.
- PostgreSQL and Keycloak cannot participate safely in one ordinary application transaction, and the future Cart Service owns another database.
- Approved behavior requires immediate denial before response, all-session revocation, retry/reconciliation within 24 hours, and non-resurrection from backups retained up to 30 days.
- Network timeouts can leave a remote effect successful with its response lost; retry must therefore identify the same operation.
- The repository currently has no Cart Service consumer, so complete COM-52/54 evidence has a cross-epic dependency.

## Options considered

### Option A — Local deny-first transaction plus durable reconciler (selected)

- **How it works:** Commit irreversible local deny/scrub/workflow intent, then process ordered idempotent remote phases with leases, backoff, alerts, and restore ledger.
- **Benefits:** Access ends even during dependency outage; response truth is local and durable; retries/restarts/lost responses are recoverable; no distributed lock/transaction.
- **Costs/risks:** Physical cleanup is eventually consistent; workflow/operations are more complex; an attention state needs operator action.
- **Evidence or uncertainty:** Directly fits approved deny/reconcile/non-resurrection requirements.

### Option B — Synchronous best-effort remote deletes before local commit

- **How it works:** Request calls Keycloak and Cart, then deletes local data if both return success.
- **Benefits:** Appears simple and may complete everything in one request when healthy.
- **Costs/risks:** Timeout creates uncertain mixed state; retry may duplicate work; dependency outage prevents local denial; a crash between calls has no durable recovery truth.
- **Evidence or uncertainty:** Cannot meet the explicit “denial before response regardless of remote cleanup” invariant safely.

### Option C — Distributed transaction/two-phase commit

- **How it works:** Enlist all data owners in a global prepare/commit protocol.
- **Benefits:** One apparent atomic outcome if every participant supports the protocol.
- **Costs/risks:** Keycloak/HTTP services are not XA participants; coordinator and lock availability become critical; future service autonomy is weakened.
- **Evidence or uncertainty:** Technically unsuitable for the selected owners and unnecessary for the required semantics.

### Option D — Publish deletion event without local deny or tracked completion

- **How it works:** Emit a fire-and-forget event and rely on consumers.
- **Benefits:** Loose producer/consumer coupling.
- **Costs/risks:** Active sessions/data can remain usable; missing consumer/poison event is invisible; no truthful 24-hour completion or restore guarantee.
- **Evidence or uncertainty:** Eventual propagation alone is not the invariant.

## Comparison

| Criterion | Importance | A: deny + reconcile | B: synchronous best effort | C: 2PC | D: fire-and-forget | Evidence/uncertainty |
|---|---:|---|---|---|---|---|
| Immediate local denial | Highest | Guaranteed at acceptance | Blocked by dependencies | Theoretical/global | Not guaranteed | Approved invariant |
| Timeout/lost-response safety | Highest | Durable/idempotent | Uncertain | Coordinator dependent | Unobserved | Required evidence |
| Keycloak/Cart feasibility | Highest | Standard APIs/events | Calls possible but unsafe | Not supported | Possible but incomplete | Owners are heterogeneous |
| Restart recovery | High | Workflow + leases | Ad hoc | Coordinator | Consumer-specific | 24-hour reconcile rule |
| Operational simplicity | Medium | Moderate | Simple happy path, complex failures | Highest complexity | Simple producer, hidden failures | Failure path is controlling |
| Backup non-resurrection | Highest | Ledger/startup gate | Not addressed | Not addressed | Not addressed | Approved requirement |

## Rationale

The business invariant is “the deleted account cannot use or recover the system,” not “every byte disappears in one request.” Making the deny boundary local provides an authoritative answer even when remote owners are unavailable. A durable workflow then turns partial failure into visible, retryable state instead of an ambiguous request result.

## Consequences

### Positive

- Old BFF handles fail immediately after the acceptance commit.
- Profile/phone/addresses are removed from active storage before `202`.
- Keycloak and Cart outages do not restore access or erase recovery intent.
- Each phase is observable, retryable after restart, and independently evidenced.
- A pseudonymous ledger prevents an older backup from serving deleted state.

### Negative/accepted costs

- `202 Accepted` means denial is committed, not that every remote byte is already removed.
- Remote cleanup requires a state machine, worker lease, retry classification, alert, runbook, and retention rules.
- A minimal pseudonymous deletion ledger persists temporarily to enforce non-resurrection.
- Full completion evidence cannot pass until the E3 Cart owner implements and acknowledges its consumer contract.

### Risks and mitigations

| Risk | Likelihood/impact | Mitigation | Evidence/owner |
|---|---|---|---|
| Keycloak delete succeeds but response is lost | Medium/medium | Operation/phase idempotency; treat not-found as success after identity match rules | Phase fault test |
| Cart event is duplicated/reordered | Medium/high | Event ID inbox dedupe; account key/security epoch ordering | E3 contract/concurrency test |
| Workflow stalls past 24 hours | Low/high | Oldest-age gauge, bounded retries, ATTENTION state, page/runbook | Operations reviewer |
| Backup restores active customer data | Low/critical | Restore deletion ledger first; reapply deny/scrub before readiness | COM-54 restore exercise |
| Ledger retained too long or too briefly | Medium/high | Proposed 90-day ceiling, purge evidence, backup-horizon arithmetic, privacy approval | Security/privacy reviewer |
| Concurrent mutation commits during deletion | Low/high | Account lock/status/epoch predicate in every write; deterministic interleaving tests | Integration evidence |

## Reversibility and migration

The architecture choice is a **two-way door before any deletion is accepted**. Once an individual deletion commits, that customer transition is deliberately one-way: rollback may disable new deletion requests or roll application code back, but it must preserve the deny state, ledger, workflow, and ability to finish cleanup.

Worker versions and event schemas change additively. A new reconciler must read old workflow phases, and rollback must not discard states created by a newer version. Moving from direct delivery to a broker keeps the outbox event ID and semantics stable and adds a transport adapter/consumer inbox.

## Revisit triggers

- Legal/product review requires a cleanup bound shorter than the current 24 hours.
- Keycloak or another identity owner supplies a different idempotent deletion/disable contract.
- Cart or another PII owner enters the system and requires a versioned consumer acknowledgement.
- Restore-test evidence shows the ledger ordering or retention cannot guarantee non-resurrection.
- Workflow backlog/retry evidence shows the direct worker cannot meet the approved bound.
- Real users or legal deletion obligations enter scope; perform a new privacy/threat review.

## Traceability and validation

| Requirement/claim | Design enforcement | Test/evidence |
|---|---|---|
| Deny before response | One local transaction updates account epoch/status and sessions before `202` | Old-handle and concurrent-write test |
| Local PII removed | Same transaction clears profile/phone and deletes addresses | Pre/post database assertion + canary scan |
| Remote cleanup within 24 h | Durable workflow, bounded retry, oldest-age alert | Phase fault/restart/time-controlled evidence |
| No resurrection | Deletion ledger restored/applied before readiness | Backup restore exercise |
| Retry does not create a new outcome | One workflow/account and stable phase operation keys | Lost-response/replay test |
| Cart cleanup | Versioned event/outbox plus E3 inbox acknowledgement | Cross-epic contract/evidence; currently blocking full Done |

## Teaching note

Distributed deletion is a consistency workflow, not a loop of API calls. First establish an authoritative local safety invariant, then make remote cleanup idempotent, durable, observable, and reconciled. A one-way business transition can still use reversible deployments—as long as rollback never reverses the business fact.
