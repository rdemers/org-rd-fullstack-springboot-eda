# 📘 Kafka Engineering Guide

> Part of the **Kafka Engineering Guide** of `org-rd-fullstack-springboot-eda`. See the [project README](../README.md).

## Stream Processing, EDA Resilience & EKS Deployment

> **Target Audience**: Java/Spring Boot developers operating Kafka consumers in containerized environments (Kubernetes / EKS).

---

## 🗂 Table of Contents

1. Fundamentals of Kafka
2. Topic Architecture & Design
3. Consumer Groups & Partitioning
4. Lifecycle & Operations
5. Reliability & Delivery Semantics
6. Deduplication Patterns
7. Kafka Transactions
8. Persistence Patterns
9. Scaling in EKS
10. Governance & Observability
11. KafkaSandbox – Testing
12. Reference Configuration

---

## 1. Fundamentals of Kafka

### General Architecture

Kafka is a distributed streaming platform organized around three roles:

* **Producers**: write messages to topics (optionally receiving ACK/NACK responses).
* **Brokers**: receive, store, and serve messages. A broker cluster forms the core of Kafka.
* **Consumers**: read messages through periodic polling. Multiple consumers can independently read the same topic.

Kafka's decoupling model is based on the fact that producers and consumers only need to agree on the data format—they never interact directly.

### Kafka Record Structure

```text
┌──────────────┐
│   Headers    │  ← Optional metadata (key/value)
├──────────────┤
│     Key      │  ← Determines the target partition
├──────────────┤
│    Value     │  ← Business data
├──────────────┤
│  Timestamp   │  ← Creation or ingestion time
└──────────────┘
```

### Topics, Partitions & Retention

* A **topic** is a logical abstraction spanning producers, brokers, and consumers.
* Each topic is divided into **partitions**—the fundamental unit of parallelism.
* Message ordering is guaranteed **only within a partition**.
* **Retention** is configurable per topic (default: one week). Data is removed by segment.
* **Compacted topics** retain only the latest record for a given key, making them useful for rebuilding state after a crash.

### Replication & Leadership

Each partition has a **leader** and one or more **followers**. Clients (producers and consumers) interact only with the leader. If the leader fails, Kafka automatically elects a follower as the new leader.

The producer's `acks` setting controls durability guarantees:

| `acks` | Behavior                     | Risk                                               |
| ------ | ---------------------------- | -------------------------------------------------- |
| `0`    | No acknowledgment            | Possible data loss                                 |
| `1`    | Leader acknowledgment only   | Data loss if the leader crashes before replication |
| `all`  | Acknowledgment from all ISRs | Maximum durability guarantee                       |

---

## 2. Topic Architecture & Design

### Fat Pipe vs Thin Pipe

Topic design is one of the most impactful architectural decisions.

#### Fat Pipe (Single Topic)

All event types are sent through a single topic.

**Drawbacks:**

* Every service filters irrelevant messages, wasting CPU, network bandwidth, and I/O.
* Schema validation becomes complex due to multiple unrelated schemas sharing the same topic.
* Defining a consistent partition key becomes difficult for heterogeneous event types, leading to hot partitions and loss of ordering guarantees.
* The system becomes harder to understand, test, and evolve.

#### Thin Pipe (Dedicated Topics)

Each event type has its own topic.

**Benefits:**

* Aligns with the single-responsibility principle of microservices.
* Strengthens domain boundaries.
* Natural and consistent partition keys.
* Simplified governance and ACL management.

**Limitations:**

* More topics increase cluster metadata overhead (guideline: approximately 4,000 partitions per broker).
* Cross-domain event ordering is no longer guaranteed.

#### The Middle Ground (Recommended)

In practice, grouping events by **aggregate or business entity** is the most effective strategy.

All events related to the same entity (for example, `Order`) share a topic and a common partition key (`orderId`), guaranteeing ordering within the aggregate without coupling unrelated domains.

**Data sensitivity** may also justify separate topics for ACL management and selective encryption.

### Partition Key & Ordering

```text
partition = hash(key) % num_partitions
```

* All messages with the same key are routed to the same partition and processed sequentially by the same consumer.
* Without a key, messages are distributed randomly or round-robin, providing no ordering guarantees.

> ⚠️ **Hot Partition**: when certain keys generate disproportionate traffic, a single partition becomes a bottleneck. Mitigation: secondary sharding (e.g., `clientId % N`).

---

## 3. Consumer Groups & Partitioning

### Principle

A **consumer group** consists of identical consumer instances sharing the same `group.id`. Kafka automatically distributes partitions among group members.

**Fundamental Rule:**

```text
Active partitions = min(number_of_consumers, number_of_partitions)
```

* If `consumers > partitions`, excess consumers remain **idle**.
* If `consumers < partitions`, each consumer processes multiple partitions.
* Optimal sizing: `number_of_consumers × threads_per_consumer = number_of_partitions` (or a divisor thereof).

### Spring Kafka Concurrency

```java
@KafkaListener(topics = "orders", concurrency = "3")
public void listen(String message) { ... }

// Or via the factory
factory.setConcurrency(3);
```

Each concurrency unit represents an independent consumer within the group.

### Consumer Group Rebalancing

A **rebalance** redistributes partitions among group members.

It is triggered by:

* Consumer joins or departures (`JoinGroup` / `LeaveGroup`)
* Exceeding `max.poll.interval.ms`
* Missing heartbeats beyond `session.timeout.ms`

#### Key Parameters

| Parameter               | Default | Purpose                          |
| ----------------------- | ------- | -------------------------------- |
| `session.timeout.ms`    | 45s     | Consumer death detection timeout |
| `heartbeat.interval.ms` | 3s      | Heartbeat frequency              |
| `max.poll.interval.ms`  | 5min    | Maximum delay between polls      |

#### Rebalance Strategies

**Eager (default):**

All processing stops during redistribution. Every partition is revoked and reassigned.

**Cooperative Sticky (recommended in production):**

Only partitions changing ownership are revoked. Other consumers continue processing uninterrupted.

```properties
partition.assignment.strategy=CooperativeStickyAssignor
```

#### Static Group Membership

By assigning a unique `group.instance.id` per consumer (for example, the pod name), Kafka can recognize a restarting consumer without triggering a rebalance, provided it rejoins before `session.timeout.ms` expires.

```yaml
spring:
  kafka:
    consumer:
      properties:
        group.instance.id: ${POD_NAME}
```

Particularly useful during Kubernetes rolling restarts.

#### Rebalance Storm

If slow consumers repeatedly exceed `max.poll.interval.ms`, they are continuously evicted and rejoin, creating a cascade of rebalances.

Mitigations:

* Increase `max.poll.interval.ms`
* Reduce `max.poll.records`
* Use Cooperative Sticky Assignor

---

## 4. Lifecycle & Operations

### Graceful Shutdown in EKS

Kubernetes pods are ephemeral.

Abrupt termination can cause:

* Unplanned rebalances
* Uncommitted offsets resulting in duplicate processing
* In-flight messages being abandoned

#### Kubernetes Configuration

```yaml
spec:
  terminationGracePeriodSeconds: 60
  containers:
    - name: kafka-app
      lifecycle:
        preStop:
          exec:
            command: ["sh", "-c", "sleep 10"]
```

#### Spring Boot Configuration

```yaml
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 60s

  kafka:
    listener:
      ack-mode: MANUAL_IMMEDIATE
    consumer:
      enable-auto-commit: false
      max-poll-records: 50
```

#### Clean Shutdown Sequence

```text
SIGTERM (Kubernetes)
    → Spring ContextClosedEvent
    → KafkaListenerContainer.stop()
    → No more poll()
    → Current batch completes
    → ack.acknowledge()
    → Offsets committed
    → Consumer closed (leaveGroup → rebalance)
```

### Pause vs Stop

| Method    | Consumer Effect                | Kafka Impact                    |
| --------- | ------------------------------ | ------------------------------- |
| `pause()` | Stops fetching new messages    | Remains in group (no rebalance) |
| `stop()`  | Stops the container completely | Immediate rebalance             |

```java
registry.getListenerContainer("myListenerId").pause();
registry.getListenerContainer("myListenerId").resume();
```

---

## 5. Reliability & Delivery Semantics

### At-Most-Once

Offsets are committed **before** processing.

Failures may cause message loss but never duplicates.

### At-Least-Once (Default)

Offsets are committed **after** processing.

Failures may lead to redelivery, requiring idempotent consumers.

```yaml
spring:
  kafka:
    consumer:
      enable-auto-commit: false
    listener:
      ack-mode: MANUAL_IMMEDIATE
```

#### ACK Modes

| Mode               | Behavior                             |
| ------------------ | ------------------------------------ |
| `MANUAL`           | Commit at the end of the poll batch  |
| `MANUAL_IMMEDIATE` | Immediate commit upon acknowledgment |
| `BATCH`            | Automatic commit after each batch    |

`MANUAL_IMMEDIATE` is generally recommended for precise offset management.

### Exactly-Once Semantics (EOS)

Kafka guarantees that the sequence **consume + process + produce** occurs exactly once from the perspective of transaction-aware consumers (`isolation.level=read_committed`).

> ⚠️ External operations (REST calls, database writes) may still execute multiple times. EOS only guarantees that downstream consumers will not see duplicate Kafka records.

### Error Handling

#### Delayed Retry

```java
acknowledgment.nack(Duration.ofSeconds(5));
```

Useful for transient failures.

#### Dead Letter Topic

```java
DefaultErrorHandler errorHandler = new DefaultErrorHandler(
    new DeadLetterPublishingRecoverer(...),
    new FixedBackOff(1000L, 3)
);
```

After N retries, unrecoverable messages are routed to a **Dead Letter Topic (DLT)** for investigation.

---

## 6. Deduplication Patterns

Duplicate messages are unavoidable in at-least-once delivery.

### Pattern 1: Idempotent Consumer

Store each processed message ID in the same database transaction as the business operation.

```sql
CREATE TABLE processed_events (
    event_id VARCHAR(36) PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL
);
```

```java
@Transactional
public void process(Event event) {
    processedEventRepository.saveAndFlush(
        new ProcessedEvent(event.getId())
    );

    orderService.save(event);
}
```

### Pattern 2: Transactional Outbox

Outgoing messages are stored in an `outbox` table within the same database transaction as the business data.

A CDC tool (such as Debezium) later publishes them to Kafka.

```java
@Transactional
public void consume(Event event, Acknowledgment ack) {
    orderService.save(event);
    outboxRepository.save(OutboxEvent.from(event));
    ack.acknowledge();
}
```

### Recommendation

Combining **Idempotent Consumer + Transactional Outbox** is considered the gold-standard approach.

The only remaining risk is duplicated REST calls, which should be handled through idempotency on the receiving side.

---

## 7. Kafka Transactions

### Idempotent Producer

Prevents duplicates caused by producer retries.

```yaml
spring:
  kafka:
    producer:
      acks: all
      retries: 2147483647
```

Kafka uses a `producerId` and `sequenceNumber` per partition to reject duplicate writes.

### Kafka Transactions (Full EOS)

```java
producer.initTransactions();
producer.beginTransaction();

try {
    producer.send(...);
    producer.sendOffsetsToTransaction(...);
    producer.commitTransaction();
} catch (Exception e) {
    producer.abortTransaction();
}
```

### `transactional.id` in Kubernetes

The `transactional.id` identifies a logical producer.

It must be:

* Stable throughout the producer's lifetime
* Unique among simultaneously active producers

```yaml
spring:
  kafka:
    producer:
      transaction-id-prefix: ${POD_NAME}-tx-
```

### ChainedKafkaTransactionManager

Synchronizes Kafka and JPA transactions.

```java
@Bean
public PlatformTransactionManager chainedTransactionManager(
        KafkaTransactionManager<String, String> kafkaTM,
        JpaTransactionManager jpaTM) {

    return new ChainedKafkaTransactionManager<>(kafkaTM, jpaTM);
}
```

> ⚠️ This is not a true atomic two-phase commit and should not replace the Transactional Outbox pattern.

---

## 8. Persistence Patterns

### Optimistic Locking

```java
@Entity
public class Order {

    @Version
    private Long version;
}
```

### Pessimistic Locking

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT o FROM Order o WHERE o.id = :id")
Optional<Order> findByIdForUpdate(@Param("id") Long id);
```

---

## 9. Scaling in EKS

### Scalability Constraints

The number of active pods can never exceed the number of partitions.

```text
Maximum active pods = number_of_partitions
Total consumers = number_of_pods × concurrency_per_pod ≤ number_of_partitions
```

### KEDA

KEDA is the recommended approach for Kafka consumer autoscaling in EKS.

```yaml
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
...
```

It scales consumers based on **consumer lag** rather than CPU or memory usage.

### KEDA vs Karpenter

|               | KEDA                        | Karpenter                     |
| ------------- | --------------------------- | ----------------------------- |
| Purpose       | Pod autoscaling             | EC2 node autoscaling          |
| Trigger       | Kafka lag, business metrics | Pod scheduling demand         |
| Scale-to-zero | ✅                           | ❌                             |
| Typical Use   | Event-driven applications   | Cluster capacity optimization |

---

## 10. Governance & Observability

### Spring Boot Health Indicators

```java
@Component
public class KafkaReadinessHealthIndicator
        implements HealthIndicator {
    ...
}
```

### Key Kafka Metrics

| Metric                                  | Description                 |
| --------------------------------------- | --------------------------- |
| `kafka_consumer_records_lag_max`        | Primary health indicator    |
| `kafka_consumer_records_consumed_total` | Consumption throughput      |
| `kafka_consumer_rebalance_total`        | Number of rebalances        |
| `kafka_listener_seconds_max`            | Maximum processing duration |
| `kafka_consumer_poll_time_max`          | Maximum poll duration       |

### Data Mesh & Data Fabric

* **Data Mesh**: organizational model where each domain owns its data as a product.
* **Data Fabric**: technical layer that automates enterprise-wide data integration.

### Data Reconciliation

Event-driven systems do not guarantee data consistency by default. Reconciliation strategies must be explicitly designed.

Recommended production approach:

```text
Fast detection      → Checksums
Audit & compliance  → Replay + Snapshots
Operational trust   → Control events
Migration/Refactor  → Shadow Consumers
```

---

## 11. KafkaSandbox – Testing

A lightweight utility built on top of `EmbeddedKafka` for unit and integration testing.

### Configuration

```java
KafkaSandbox sandbox = KafkaSandbox.builder()
    .autoStart(true)
    .addTopic(...)
    .build();
```

### Transactional Producer Pool

```java
sandbox.createTransactionalProducerPool("payments", 4);
```

> Golden Rule: A `KafkaProducer` is thread-safe, but a transaction is not. A transactional producer must be owned by a single thread at a time.

---

## 12. Reference Configuration

### application.yml (Production EKS)

```yaml
server:
  shutdown: graceful

spring:
  lifecycle:
    timeout-per-shutdown-phase: 60s

  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}

    consumer:
      enable-auto-commit: false
      max-poll-records: 50
      isolation-level: read_committed

    listener:
      ack-mode: MANUAL_IMMEDIATE

    producer:
      acks: all
      retries: 2147483647
      transaction-id-prefix: ${POD_NAME}-tx-
```

### Quick Reference

| Parameter                       | Recommended Value           | Reason                     |
| ------------------------------- | --------------------------- | -------------------------- |
| `enable.auto.commit`            | `false`                     | Manual offset control      |
| `ack-mode`                      | `MANUAL_IMMEDIATE`          | Precise commits            |
| `isolation.level`               | `read_committed`            | Ignore uncommitted records |
| `acks`                          | `all`                       | Maximum durability         |
| `partition.assignment.strategy` | `CooperativeStickyAssignor` | Less disruptive rebalances |

### Anti-Patterns to Avoid

| ❌ Anti-Pattern                                        | ✅ Recommended Practice                         |
| ----------------------------------------------------- | ---------------------------------------------- |
| `enable.auto.commit=true`                             | Use manual acknowledgments                     |
| Commit before processing                              | Commit after processing                        |
| Regenerating `transactional.id` at startup            | Use a stable pod-based identifier              |
| Sharing transactional producers across threads        | One transactional producer per thread          |
| ACK during shutdown                                   | Return without ACK to guarantee redelivery     |
| Combining Idempotent Consumer with Kafka Transactions | Use Idempotent Consumer + Transactional Outbox |
