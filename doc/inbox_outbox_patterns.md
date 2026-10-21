# Inbox and Outbox Design Patterns

> Part of the **Kafka Engineering Guide** of `org-rd-fullstack-springboot-eda`. See the [project README](../README.md).

**Scope :** Presentation and implementation examples of design patterns associated with distributed systems, EDA, Apache Kafka, and transactional databases. The SQL examples primarily target PostgreSQL and should be adapted to the chosen DBMS.

## Table of Contents

1. [Background and Problem Statement](#1-background-and-problem-statement)
2. [Principles and Guarantees](#2-principles-and-guarantees)
3. [Transactional Outbox](#3-transactional-outbox)
4. [Transactional Inbox](#4-transactional-inbox)
5. [Combining Inbox and Outbox](#5-combining-inbox-and-outbox)
6. [References](#6-references)

---

## 1. Background and Problem Statement

In an event-driven architecture (EDA), a service often needs to **modify a database** and **emit an event**. Another service must then **consume that event** and **modify its own database**. These operations cross different transactional boundaries.

### 1.1 The dual-write problem

Example: an order is recorded in PostgreSQL, then `OrderCreated` is published to Kafka.

```text
Order Service
    |-- INSERT order   --> PostgreSQL
    `-- PRODUCE event --> Kafka
```

Two independent systems do not automatically participate in the same ACID transaction. If the database commits and the process crashes before publishing, the order exists but its event is missing. If the event is published before the SQL commit, consumers may receive an event describing an operation that is ultimately rolled back.

| Naive sequence | Failure window | Consequence |
| --- | --- | --- |
| SQL commit, then Kafka | Crash after commit | Lost event |
| Kafka, then SQL commit | SQL rollback | Phantom event |
| Kafka and SQL without coordination | Partial failures | Divergent states |

### 1.2 The symmetric problem on the consumer side

A consumer modifies a SQL database but crashes before committing its Kafka offset. Kafka may redeliver the same event. Without deduplication, the business change is applied a second time: double debit, double credit, incorrect inventory, or inflated statistics.

**The two patterns address different risks:**

- **Outbox**: avoid losing the intent to publish an event associated with a committed business transaction.
- **Inbox**: prevent the same event from causing the same transactional business effects multiple times.

They generally eliminate the need for a distributed two-phase commit (2PC) between Kafka and the database.

## 2. Principles and Guarantees

### 2.1 Local atomicity

The guaranteed atomicity is **that of the local database**: the business write and the Inbox/Outbox record must be performed in **the same transaction and transactional scope**. Two `save()` calls without a common transaction are not sufficient.

### 2.2 Delivery and processing

| Concept | Definition | Typical guarantee |
| --- | --- | --- |
| At-most-once | No repetition, but loss is possible | Unsuitable for critical effects |
| At-least-once | Retries until delivery; duplicates possible | Outbox with a reliable relay |
| Effectively-once | Multiple deliveries, one observable business effect | Atomic Inbox + covered operations |
| Kafka exactly-once | Kafka guarantees within a defined scope | Does not make an external SQL transaction atomic |

**Important:** “exactly once” does not mean “one and only one network delivery.” External effects (email, payments, remote APIs) require their own idempotency protections.

### 2.3 Identities that must be distinguished

- `event_id`: stable identity of a logical event, unchanged across retries.
- `aggregate_id`: identity of the business entity (order, inventory, account).
- `consumer_id`: logical and **stable** identity of a handler or subscriber; it is not a thread, a pod instance, or necessarily the Kafka `group.id`.
- `correlation_id`: connects steps in a business process.
- `causation_id`: references the event or command that caused this event.
- `event_type`: type of business fact, such as `InventoryReserved`.
- `status`: technical processing state, such as `PENDING`, `PROCESSING`, `COMPLETED`, or `FAILED`.
- `schema_version`: contractual version of the message.

## 3. Transactional Outbox

### 3.1 Purpose

A business transaction writes **both** the business data and an Outbox record. A separate process then publishes that record to Kafka.

```text
             Single SQL transaction
API ---> Service ----+----> Business UPDATE/INSERT
                     `----> INSERT outbox (PENDING)
                            |
                          COMMIT
                            |
                   Relay / CDC / polling
                            |
                         Kafka topic
```

### 3.2 Normal sequence

1. Receive the command and validate business rules.
2. Begin a SQL transaction.
3. Modify business data.
4. Create an immutable event with `event_id`, aggregate key, and version.
5. Insert the event into `outbox_event`.
6. Commit the SQL transaction: both writes become durable.
7. The relay discovers the committed event and sends it to the broker.
8. After the broker durably acknowledges it, the relay records success, or CDC handles progress tracking.

### 3.3 Invariants

- **SQL rollback**: neither the business change nor the new Outbox row persists.
- **SQL commit**: the publishing intent is durable, even when Kafka is unavailable.
- **Repeated publication is possible**: a crash after the Kafka ACK but before the status update triggers another publication.
- **No zero-latency guarantee**: the relay runs asynchronously.
- **Ordering is not automatic**: it must be designed per aggregate and preserved by the relay and Kafka partitioning.

### 3.4 What the pattern does not guarantee by itself

It guarantees neither receipt by all subscribers, nor the absence of duplicates, nor global ordering across aggregates, nor unlimited data availability in the event of permanent failure or misconfiguration. It does not replace monitoring and recovery procedures.

## 4. Transactional Inbox

### 4.1 Purpose

The Inbox is a durable registry of received or processed events. The **minimal** variant stores the identity of each applied event; the **durable, stateful** variant also persists the payload and allows ingestion to be separated from processing.

```text
Kafka ---> Consumer ---> BEGIN SQL
                         |
                         +--> INSERT inbox (consumer_id, event_id)
                         |        UNIQUE (consumer_id, event_id)
                         +--> UPDATE business data
                         |
                         `--> COMMIT SQL
                                |
                         Commit Kafka offset
```

### 4.2 “Processed events” variant

The consumer first attempts an `INSERT` protected by a `UNIQUE` constraint. If the key already exists, the event has been processed (according to this table's contract): no new business effect is applied. Otherwise, the insertion and the business changes are committed together.

**Do not implement** `SELECT exists?` followed by `INSERT` without a unique constraint: two concurrent consumers could both pass the initial check.

### 4.3 Durable Inbox variant

A short transaction persists the incoming message and its metadata. A separate worker processes `RECEIVED`/`RETRY` messages, using atomic state transitions, locking, and recovery. This variant supports decoupling and replay but requires a **second processing transaction** that commits the business effects and final status together. Do not mark a message `COMPLETED` before the business effects are committed.

### 4.4 Normal sequence

1. Receive the Kafka message and validate its envelope.
2. Determine the logical `consumer_id` and stable `event_id`.
3. Begin a SQL transaction.
4. Attempt to insert the unique Inbox key.
5. If the key is new, apply business changes in the same transaction.
6. Commit SQL; if the key existed, do not reapply the effects.
7. Commit the Kafka offset **after** the SQL commit, respecting offset commit ordering within the partition.

### 4.5 Important case: failure after SQL commit

The SQL commit succeeds, but the process stops before the offset commit. Kafka redelivers the event. The unique constraint identifies the duplicate; the consumer does not repeat the business effects and can commit the offset after verifying that processing has already occurred.

### 4.6 Scope of the registry

A `(consumer_id, event_id)` key allows multiple independent handlers to legitimately consume the same event. If the contract requires reprocessing an event with a new version of business logic, use a versioned handler identity or an explicit replay procedure rather than arbitrarily removing protections.

## 5. Combining Inbox and Outbox

An intermediate service can consume `OrderCreated`, update its own tables, and then produce `InventoryReserved`.

```text
Service A                  Kafka                Service B                  Kafka
---------                  -----                ---------                  -----
Business SQL + OUTBOX ---> OrderCreated ---> INBOX + Business SQL + OUTBOX ---> InventoryReserved
       (T1)                                        (T2)
```

Within **T2**, Service B atomically writes:

1. The Inbox marker for `OrderCreated`.
2. The inventory reservation.
3. The new `InventoryReserved` Outbox event.

If T2 fails, all three writes roll back. If T2 succeeds but the Kafka offset is not committed, redelivery does not create a second reservation or a second Outbox event. However, the output event may still be **published** multiple times by the relay.

**Key invariant:** `INBOX + BUSINESS + OUTBOX` in **one local SQL transaction**, followed by Kafka ACK/commit after that transaction.

## 6. References

Reference material and further reading:

1. Chris Richardson, *Transactional Outbox*: <https://microservices.io/patterns/data/transactional-outbox.html>
2. Chris Richardson, *Polling Publisher*: <https://microservices.io/patterns/data/polling-publisher.html>
3. Chris Richardson, *Transaction Log Tailing*: <https://microservices.io/patterns/data/transaction-log-tailing.html>
4. Microsoft Azure Architecture Center, *Idempotent Consumer Pattern*: <https://learn.microsoft.com/en-us/azure/architecture/patterns/idempotent-consumer>
5. Apache Kafka, *Design / Delivery Semantics*: <https://kafka.apache.org/documentation/#semantics>
6. Debezium, *Outbox Event Router*: <https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html>
7. PostgreSQL, *Explicit Locking*: <https://www.postgresql.org/docs/current/explicit-locking.html>
8. Spring for Apache Kafka, *Reference Documentation*: <https://docs.spring.io/spring-kafka/reference/>

---

**Conclusion.** The Outbox preserves the intent to publish after a committed business transaction; the Inbox makes transactional business effects idempotent in the face of redelivery. Together, they enable resilient EDA processing chains, provided that ordering, transactions, retries, external effects, operations, and replay are explicitly addressed.
