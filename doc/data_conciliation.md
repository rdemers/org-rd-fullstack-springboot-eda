# Data conciliation

> Part of the **Kafka Engineering Guide** of `org-rd-fullstack-springboot-eda`. See the [project README](../README.md).

**Scope:** how to verify data integrity and consistency across an event-driven system — reconciling a source of truth against derived views, cross-checking sources, detecting divergence, and auditing — and how this sandbox applies those ideas to its request/inventory pipeline.

## Table of contents

- [Overview](#overview)
- [Why eventual consistency makes conciliation necessary](#why-eventual-consistency-makes-conciliation-necessary)
- [Reconciliation patterns](#reconciliation-patterns)
  - [Source of truth vs derived views](#source-of-truth-vs-derived-views)
  - [Event replay and reconciliation jobs](#event-replay-and-reconciliation-jobs)
  - [Periodic snapshots](#periodic-snapshots)
  - [Count and checksum conciliation](#count-and-checksum-conciliation)
  - [Control and checkpoint events](#control-and-checkpoint-events)
  - [Shadow consumers](#shadow-consumers)
  - [Business invariants](#business-invariants)
- [Detecting and handling divergence](#detecting-and-handling-divergence)
- [Relationship to idempotency and exactly-once](#relationship-to-idempotency-and-exactly-once)
- [How this project applies it](#how-this-project-applies-it)
  - [The processor and the request counts](#the-processor-and-the-request-counts)
  - [Internal conciliation](#internal-conciliation)
  - [Reading the findings](#reading-the-findings)
  - [Running it from the UI](#running-it-from-the-ui)
  - [Limits of the sandbox implementation](#limits-of-the-sandbox-implementation)
- [Pitfalls & best practices](#pitfalls--best-practices)

## Overview

In an event-driven architecture (EDA), no single mechanism guarantees that every consumer and downstream store ends up with the same view of reality. Message delivery semantics, consumer crashes, partition rebalances, and transient network faults can all produce **divergence** between systems that are supposed to agree.

![Conciliation](./asserts/conciliation.png)

**Data conciliation** is the deliberate practice of confirming that two or more representations of the same facts are consistent: a source of truth and a derived projection, two independent consumers, or a recomputed state and a persisted state. It complements — but does not replace — delivery guarantees and idempotency. Where idempotency prevents a single message from corrupting state, conciliation *detects* when state has drifted anyway and gives you the evidence to repair it.

The key mindset shift is this:

> Event delivery does not guarantee global consistency. Conciliation must be designed, implemented, and operated deliberately.

## Why eventual consistency makes conciliation necessary

Kafka offers **at-least-once** delivery by default: a message is delivered at least once, but under failure conditions (a crash after processing but before the offset commit, a rebalance, a pod eviction in Kubernetes) it can be delivered again. The classic failure windows are:

- The consumer receives a message and crashes before processing it.
- The consumer processes the message and crashes before acknowledging it.
- A pod is evicted or restarted mid-flight.
- Network problems interrupt the commit.

Each of these can leave the system in a state where the events that *were* emitted no longer match the state that *was* persisted — duplicates applied twice, or work re-attempted on partial state. Because consumers update their own stores independently and asynchronously, the system as a whole is only **eventually consistent**. Conciliation is what turns "eventually" from a hope into a verifiable, auditable property.

```mermaid
flowchart LR
    P[Producer] -->|events| K[(Kafka topic)]
    K --> C1[Primary consumer]
    K --> C2[Downstream store]
    C1 --> S1[(Derived state A)]
    C2 --> S2[(Derived state B)]
    S1 -. reconcile .- S2
    R[Reconciliation / conciliation] --- S1
    R --- S2
```

## Reconciliation patterns

In production-grade EDA, conciliation is rarely a single technique. It is a **layered** set of mechanisms with different cost/latency trade-offs:

| Objective | Technique |
| --- | --- |
| Fast divergence detection | Counts, checksums / hashes |
| Audit & compliance | Event replay and snapshots |
| Operational confidence | Control / checkpoint events |
| Migration & refactoring | Shadow consumers |
| Foundational safety | Idempotency and sequencing |

### Source of truth vs derived views

The first design decision is naming the **source of truth**. In an event-sourced flavour this is the event log itself (Kafka, an event store); everything else — search indexes, read models, aggregate counters — is a **derived view** that can be recomputed from the log. Conciliation then reduces to a single question: *does this derived view still match what the source of truth implies?*

In a more database-centric system (like this sandbox), the relational tables are the authoritative state and the event stream is the *trigger* for mutating them. The conciliation question flips slightly: *does the persisted state match the cumulative effect of the events we processed?*

### Event replay and reconciliation jobs

Reprocess historical events from the source of truth to recompute the expected state, then compare it with the persisted state.

```text
Event Store (Kafka)
        |
Reconciliation consumer (dedicated group, reads from offset 0)
        |
Expected state  <->  Actual state
```

- Use a **dedicated consumer group** so replay does not disturb live offsets.
- Read from the beginning (or a known checkpoint) and rebuild the expected state.
- Diff expected vs actual; report discrepancies.

**Pros:** deterministic, strong auditability. **Cons:** expensive at high volume, not real-time.

### Periodic snapshots

Periodically materialise a snapshot of the business state derived from events and compare it with downstream systems on a schedule (hourly, daily).

```json
{
  "productId": "P123",
  "availableStock": 42,
  "snapshotAt": "2026-10-01T00:00:00Z"
}
```

Implement with a batch or stream job (Spring Batch, Spark, Flink) writing to a dedicated topic such as `inventory-snapshots`, keyed by business key plus a timestamp or version. **Pros:** low operational cost, easy to audit. **Cons:** delayed detection, no real-time guarantee.

### Count and checksum conciliation

The cheapest signals are **aggregate counts** and **checksums**.

- **Counts:** compare the number of processed records, grouped by outcome, against the number of effects they should have produced. A discrepancy in totals is a fast, low-volume tripwire.
- **Checksums:** each consumer computes a hash (e.g. SHA-256) over its derived state — or a critical subset — and publishes it periodically; hashes are compared across systems.

```text
Derived state --> SHA-256 --> state-checksum-topic
```

**Pros:** very fast, tiny data volume, early detection. **Cons:** signals *that* there is divergence, not *where*; checksums require a deterministic state representation (stable ordering, canonical encoding).

### Control and checkpoint events

Emit technical events used purely for validation and monitoring — `InventoryCheckpointReached`, `EndOfDayProcessed`, `SequenceGapDetected`. Consumers acknowledge or react to them, giving operators a heartbeat for end-to-end progress. **Pros:** simple, effective in production monitoring. **Cons:** not exhaustive; complementary rather than standalone.

### Shadow consumers

Run an independent "shadow" consumer that rebuilds state in parallel and continuously compares it with the primary consumer's output. Ideal for **consumer refactoring**, **logic changes**, and **platform migrations**: you gain near real-time validation and high confidence at the cost of extra infrastructure and operational complexity.

### Business invariants

Define invariants that must always hold and validate them automatically:

- Inventory level must never be negative.
- Account balances stay consistent.
- Daily totals reconcile.

Validate via stream processing, periodic batch jobs, or alerting. Invariant violations are often the *first* externally visible symptom of silent divergence.

## Detecting and handling divergence

Detection is necessary but not sufficient — you also need a response playbook:

1. **Detect** with a cheap, frequent signal (counts/checksums) and a periodic deep check (snapshots/replay).
2. **Localise** by drilling from the aggregate signal down to the offending business keys.
3. **Classify** the cause: duplicate application, missed event, out-of-order processing, or a genuine bug.
4. **Repair** by replaying from the source of truth, re-deriving the view, or issuing a compensating event — never by hand-editing derived state without an audit trail.
5. **Record** the discrepancy and the remediation for audit and trend analysis.

```mermaid
flowchart TD
    A[Cheap signal: counts / checksums] -->|match| OK[No action]
    A -->|mismatch| B[Deep check: snapshot or replay]
    B --> C[Localise to business keys]
    C --> D[Classify cause]
    D --> E[Repair: replay / re-derive / compensate]
    E --> F[Audit log]
```

## Relationship to idempotency and exactly-once

Idempotency and sequencing are **not** conciliation mechanisms by themselves, but without them every reconciliation strategy is fragile:

- **Idempotency** ensures that reprocessing the same event (during replay, retry, or rebalance) does not double-apply effects. This is what makes safe replay — and therefore replay-based conciliation — possible.
- **Business-level sequencing** (a monotonic sequence number per aggregate) lets you detect duplicates and gaps:

```json
{
  "eventType": "StockDecreased",
  "productId": "P123",
  "sequence": 184
}
```

- **Exactly-once** semantics (Kafka transactions, the transactional outbox) shrink the window in which divergence can occur, but they are bounded by transactional scope. The moment effects cross a boundary the platform cannot enroll in one transaction — for instance, an external store or a separate database — at-least-once reappears and conciliation is again required.

In short: idempotency + sequencing make state *safe to recompute*; conciliation *checks whether you needed to*.

## How this project applies it

This sandbox is database-centric: the relational `Request`, `Person` and `Inventory` tables are the authoritative state, and Kafka messages trigger their mutation. Every processed event is recorded in a journal (`JRN_EVENT`), which makes it possible to reconcile the persisted state against what the events say happened. Three artefacts are central here: the idempotent processor, a cheap count probe, and the full [internal conciliation](#internal-conciliation).

> **Terminology.** This guide uses *conciliation* as the project term for both the general practice and this project's implementation, [`ConciliationSrv`](../src/main/java/org/rd/fullstack/springbooteda/srv/ConciliationSrv.java).

### The processor and the request counts

**The processor** — [`ProcessorSrv`](../src/main/java/org/rd/fullstack/springbooteda/srv/ProcessorSrv.java) — processes each request in its own JPA transaction and is explicitly **idempotent**: a request whose result is no longer `PENDING`/`BACK_ORDER` has already been handled and is skipped. This is precisely the property that makes the at-least-once redelivery of the Kafka error handler safe, and that would make a replay-based reconciliation job safe to run.

```java
// ProcessorSrv.process(...) — idempotency guard
if ((request.getResult() != Result.PENDING) &&
    (request.getResult() != Result.BACK_ORDER))
    return; // already processed -> skip
```

Each request transitions through the result states **PENDING -> BACK_ORDER / EXECUTED / ERROR**, and every state-changing path also mutates inventory through [`InventoryRepository`](../src/main/java/org/rd/fullstack/springbooteda/dao/InventoryRepository.java) (`creditQTY` / `debitQTY`). That coupling is the natural conciliation target: the number of `EXECUTED` `CREDIT`/`DEBIT` requests should reconcile with the net inventory movement they caused.

**The count aggregation** — [`RequestRepository.countRequest()`](../src/main/java/org/rd/fullstack/springbooteda/dao/RequestRepository.java) — is a ready-made **count conciliation** probe. It returns a `RequestCount` summarising how many requests sit in each result state:

```sql
SELECT new ...RequestCount(
    SUM(CASE WHEN req.result = 10 THEN 1 ELSE 0 END) AS nbrPending,
    SUM(CASE WHEN req.result = 20 THEN 1 ELSE 0 END) AS nbrBackOrder,
    SUM(CASE WHEN req.result = 30 THEN 1 ELSE 0 END) AS nbrExecuted,
    SUM(CASE WHEN req.result = 99 THEN 1 ELSE 0 END) AS nbrError,
    SUM(CASE WHEN req.result NOT IN (10,20,30,99) OR req.result IS NULL
        THEN 1 ELSE 0 END) AS nbrUnknown)
FROM Request req
```

Practical conciliation uses for this aggregate in the sandbox:

- **Pipeline progress / heartbeat:** a stable, non-zero `nbrPending` over time means the pipeline has stalled — a control-event-style signal computed cheaply on demand.
- **Completeness check:** the sum of all buckets must equal the total number of submitted requests. The explicit `nbrUnknown` bucket (anything outside `10/20/30/99` or `NULL`) is a built-in invariant tripwire for corrupt or unexpected state values.
- **Cross-source reconciliation:** a count only says *that* totals differ. Reconciling `EXECUTED` requests against the actual client and inventory movements — and telling *where* they differ — is the job of the [internal conciliation](#internal-conciliation) below.

### Internal conciliation

[`ConciliationSrv`](../src/main/java/org/rd/fullstack/springbooteda/srv/ConciliationSrv.java) is the deep check that complements the cheap count probe. It cross-checks four independent sources against each other and **only reads**: it never modifies the journal, the batch files or the business tables, and it reports anomalies as findings instead of throwing.

```mermaid
flowchart LR
    DLT[("Dead Letter Topics")] --> C{{ConciliationSrv}}
    J[("JRN_EVENT journal")] --> C
    F["Batch files<br/>log/batch-id.trx"] --> C
    T[("Person balances<br/>Inventory quantities")] --> C
    C --> R["List of findings<br/>step, severity, message"]
```

The journal `payload` of an `EXECUTED` event holds the movement it caused, as a pair of *legs* (one for the client, one for the inventory). The `operation` is signed:

```json
{
  "movement":{
     "client":   { "id": 7, "begin": 10.00, "operation": -5.00, "end": 5.00},
     "inventory":{ "id": 3, "begin": 10,    "operation": -2,    "end": 8}
  }
}
```

Here, `client.id` is the person id and `inventory.id` is the inventory primary key. The execution batch is read from `JrnEvent.batchId`; it is not duplicated in the movement payload. A replay updates that journal column to the replay batch, so the journal value is the source of truth used by conciliation.

The conciliation runs five steps, each reported under its own `Step`:

| Step | What is verified | Pattern from this guide |
| --- | --- | --- |
| `DLT` | Collects the event ids sitting in every Dead Letter Topic, so dead-lettered transactions are not mistaken for lost ones. | Source of truth vs derived views |
| `JOURNAL` | Loads the journal and groups it by batch id. | Source of truth vs derived views |
| `BATCH_FILE` | Each batch has its confirmed `.trx` file; every transaction of the file is in the journal (or a DLT); every journal event of the batch is in the file (no orphan). | Count conciliation |
| `CLIENT_BALANCE` | Replays the movements in processing order: `BALANCE = BALANCE ± COST`. | Event replay, business invariants |
| `INVENTORY_QTY` | Replays the movements in processing order: `QTY = QTY ± QTY`. | Event replay, business invariants |

For the two replay steps, only `EXECUTED` events are considered (`PENDING`, `BACK_ORDER` and `ERROR` moved nothing), sorted by `processedAt` then journal id. For each client or inventory record the service checks that:

1. **the arithmetic holds** — `begin + operation = end`;
2. **the chain is unbroken** — each movement begins where the previous one ended, **within one execution batch**;
3. **the entity still exists**;
4. **the latest batch agrees with the database** — as a separate second-stage check, the last `end` equals the live balance or quantity, checked **only when the last movement belongs to the latest batch**.

The payload replay itself does not use the live `Person.balance` or `Inventory.qty` values. It validates the movement arithmetic and chain from the journal payload only. The database values are read once as a snapshot for the independent payload-versus-database check.

The chain is checked per execution batch because the business tables can legitimately change between two runs — typically a manual inventory refill after the first run, before a replay re-executes the back-ordered requests. Such changes are not journaled, so they would otherwise show up as a broken chain. When more than one batch is present, a `WARN` says so (the `JOURNAL` step also warns if the journal itself holds several batch ids). The current `JrnEvent.batchId` remains the source of truth, including after a replay.

An `EXECUTED` event whose payload holds no valid movement is itself an error.

### Reading the findings

The outcome is a flat list of findings, `Finding(step, severity, message)`:

| Severity | Meaning | Examples |
| --- | --- | --- |
| `INFO` | A check ran and found nothing wrong, or reports a figure. | `3 event(s) found in the DLTs.`, `Batch 12: 40 transaction(s), consistent.` |
| `WARN` | The check ran in a degraded way, or found something suspicious but not corrupting. | DLT unreadable or timed out; duplicate event in a batch file. |
| `ERROR` | An integrity break: sources disagree. | Missing `.trx` file; orphan journal event; broken chain; replay ends at a value different from the database. |

A run is clean when it contains no `ERROR`. Anomalies are the *content* of the report, so the endpoint answers HTTP 200 even when errors are found.

A broken chain is a genuine signal, not a bug of the checker: the inventory oversell race this sandbox teaches is expected to surface in the `INVENTORY_QTY` step, where two concurrent movements begin from the same quantity.

### Running it from the UI

- **REST:** `GET /api/conciliation/findings` ([`ConciliationController`](../src/main/java/org/rd/fullstack/springbooteda/controller/ConciliationController.java)), role `ROLE_SELECT`. Returns the `Finding` list.
- **Screen:** the **Findings** tab at the bottom of the journal page (`/jrn-events`). The conciliation is **never run automatically**: it reads the whole journal, the batch files and the DLTs, and can take several seconds. Press *Run conciliation*; the table lists the findings, most severe first, and the tab shows a badge with the number of `ERROR`s.

### Limits of the sandbox implementation

This is a sandbox-only service, meant to teach the idea rather than to be used in production.

- **Basic checks only.** A real conciliation would be far more extensive.
- **No access control beyond the role**, and no pagination: the whole journal is loaded in memory, so it does not scale to production volumes.
- **DLT reading is bounded** (30-second budget) and uses a throw-away consumer that does not commit offsets, so it does not disturb live consumer groups.
- **`JrnEvent.batchId` is the batch source of truth.** A replay updates that journal column to the replay batch; the payload does not duplicate the batch id. Batch-file checks therefore use the current batch id stored on the journal row.

## Pitfalls & best practices

- **Treat conciliation as a first-class concern**, not a by-product of consumption. If it is not designed in, it will not happen.
- **Layer your checks**: cheap and frequent (counts/checksums) for early warning, expensive and periodic (snapshots/replay) for ground truth.
- **Make state deterministic** before you checksum it — unstable ordering or non-canonical encoding produces false divergence alarms.
- **Never repair derived state by hand** without an audit trail; prefer replay, re-derivation, or compensating events.
- **Guard idempotency and sequencing first** — they are the foundation that makes replay and reconciliation safe.
- **Mind the transaction boundary**: exactly-once shrinks the divergence window but does not remove the need for conciliation across stores it cannot enroll.
- **Alert on the cheap signal, investigate with the deep one** — don't run full replays on every tick.
- **Account for in-flight work**: a transient `nbrPending` is normal; only a *persistently* non-decreasing pending count indicates a stall.
- **Do not run the deep check on a live, busy pipeline and trust it blindly**: events still in flight can show up as transient `ERROR`s (an orphan, a replay not yet matching the database). Re-run once the pipeline is quiet before concluding.
- **Treat a broken chain as evidence, not noise**: a movement that does not begin where the previous one ended points to a concurrency or ordering defect worth investigating.

