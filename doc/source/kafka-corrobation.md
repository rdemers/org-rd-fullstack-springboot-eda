# Data Corroboration in Event-Driven Architectures (EDA)

In an Event-Driven Architecture (EDA), data consistency across multiple consumers and downstream systems is not guaranteed by default. Message delivery semantics, consumer failures, rebalances, and transient infrastructure issues can all lead to data divergence.

For this reason, **data corroboration and reconciliation must be treated as explicit architectural concerns**, not as incidental by-products of event consumption.

This document outlines the most common and proven approaches to corroborate data received via EDA, combining real-time mechanisms and periodic validation processes.

---

## 1. Event Replay and Reconciliation Jobs

### Principle

Reprocess historical events from a source of truth (Kafka, Event Store) to recompute the expected state and compare it with the current persisted state.

### Use Cases

- Detecting data drift
- Post-incident recovery
- Audit and compliance

### Implementation

- Dedicated consumer group
- Read from historical offsets or from the beginning
- Rebuild expected state
- Compare expected vs actual state

```text
Event Store (Kafka)
        ↓
Reconciliation Consumer
        ↓
Expected State ↔ Actual State

Pros / Cons

Pros

Deterministic and reliable

Strong auditability

Cons

Expensive on large volumes

Typically not real-time

### 2. Periodic Snapshots (State Summaries)
Principle

Periodically generate a snapshot of the business state derived from events and compare it with downstream systems.

Example (E-commerce Inventory)

Theoretical stock derived from events

Actual stock stored in a database

Comparison performed hourly or daily

Implementation

Batch job (Spring Batch, Spark, Flink)

Dedicated topic (e.g. inventory-snapshots)

Business key + timestamp or version

{
  "productId": "P123",
  "availableStock": 42,
  "snapshotAt": "2026-10-01T00:00:00Z"
}

Pros / Cons

Pros

Low operational cost

Easy to audit

Cons

Detection is delayed

Not suitable for real-time guarantees

3. State Checksums / Hash Validation
Principle

Each consumer computes a checksum (hash) of its derived state (or a critical subset) and publishes it periodically.

The checksums are compared across systems to detect divergence.

Derived State → SHA-256 → state-checksum-topic

Pros / Cons

Pros

Very fast

Low data volume

Early divergence detection

Cons

Does not identify the root cause

Requires deterministic state representation

4. Control and Checkpoint Events
Principle

Emit technical control events used exclusively for validation and monitoring purposes.

Examples

InventoryCheckpointReached

EndOfDayProcessed

SequenceGapDetected

Consumers acknowledge or react to these events, enabling operational verification.

Pros / Cons

Pros

Simple to implement

Effective in production monitoring

Cons

Not exhaustive

Complementary rather than standalone

5. Idempotency and Business-Level Sequencing

This is not a corroboration mechanism by itself, but without it, all reconciliation strategies are fragile.

Best Practices

Business-level sequence numbers per aggregate

Gap detection

Rejection or buffering of out-of-order events

{
  "eventType": "StockDecreased",
  "productId": "P123",
  "sequence": 184
}

Benefits

Detects duplicates and missing events

Enables safe replay

Foundational for auditability

6. Shadow Consumers (Parallel Validation)
Principle

Run an independent “shadow” consumer that rebuilds state in parallel and continuously compares it with the primary consumer’s output.

Use Cases

Consumer refactoring

Logic changes

Platform migration

Pros / Cons

Pros

Near real-time validation

High confidence

Cons

Additional infrastructure cost

Increased operational complexity

7. Contractual Business Invariants
Principle

Define business invariants that must always hold true and validate them automatically.

Examples

Inventory level must never be negative

Account balance consistency

Daily totals reconciliation

Validation can be performed via:

Stream processing

Periodic batch jobs

Alerting systems

Recommended Strategy

In production-grade EDA systems, data corroboration is usually implemented as a layered approach:

Objective	Technique
Fast divergence detection	Checksums / hashes
Audit & compliance	Replay and snapshots
Operational confidence	Control events
Migration & refactoring	Shadow consumers
Key Takeaway

Event delivery does not guarantee global consistency.
Data corroboration in EDA must be designed, implemented, and operated deliberately, combining real-time validation, periodic reconciliation, and strong audit capabilities.
