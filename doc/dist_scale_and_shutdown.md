# Scale and shutdown in distributed environments

> Part of the **Kafka Engineering Guide** of `org-rd-fullstack-springboot-eda`. See the [project README](../README.md).

**Scope:** how Kafka consumer parallelism scales in Kubernetes/EKS — the relationship between pods, threads and partitions, and why partition count caps achievable parallelism — and why the ephemeral nature of pods makes graceful shutdown a hard requirement, with concrete Spring Boot / Spring Kafka patterns for stopping consumers and flushing producers cleanly to avoid rebalances, duplicates and message loss.

## Processes and Threads in EKS

In a Kubernetes environment such as **Amazon EKS**, Kafka consumers are typically deployed as **multiple pod replicas**. Each pod runs a **consumer process**, and within that process, the application may use one or more **threads** to process records.

Kafka enforces a strict rule: **a partition can be consumed by only one consumer instance within a consumer group at any given time**. This means:

* Scaling consumer pods increases parallelism **only up to the number of partitions**.
* Threads within a single consumer process do not increase Kafka-level parallelism unless they are explicitly mapped to independent partitions.

As a result, the number of partitions in a topic effectively defines the **maximum achievable parallelism** for consumers, regardless of how many pods or threads are deployed.

## Ephemeral Pods and Graceful Shutdown

In **Amazon EKS**, pods are inherently **ephemeral**. They can be terminated or replaced at any time due to scaling events, rolling deployments, node failures, or rescheduling decisions made by the Kubernetes control plane.

For Kafka producers and consumers, this ephemeral nature makes **graceful shutdown handling a critical requirement** rather than an optional optimization.

When a consumer pod is terminated abruptly:

* Partition ownership may be revoked unexpectedly, triggering a **consumer group rebalance**.
* In-flight messages may be left unprocessed or processed more than once.
* Offsets may not be committed correctly, leading to **message duplication or data loss**, depending on the delivery semantics.

To mitigate these risks, Kafka applications running in EKS must:

* Handle Kubernetes termination signals (`SIGTERM`) properly.
* Stop polling new records and allow in-flight processing to complete.
* Commit offsets before shutdown (for consumers).
* Close Kafka clients gracefully to ensure partition revocation and rebalance occur cleanly.

Similarly, Kafka producers must ensure that buffered records are flushed and acknowledged before the pod terminates, preventing message loss.

Proper implementation of **graceful shutdown logic** ensures that Kafka workloads remain resilient, consistent, and predictable, even in a highly dynamic and elastic Kubernetes environment.

---

## Graceful Shutdown with Spring Boot and Spring Kafka on EKS

Spring Boot and Spring Kafka provide built-in mechanisms that integrate well with Kubernetes pod lifecycle events, making graceful shutdown both achievable and reliable when properly configured.

### 1. Kubernetes Configuration (EKS)

First, ensure that Kubernetes allows enough time for the application to shut down gracefully:

```yaml
spec:
  terminationGracePeriodSeconds: 30
  containers:
    - name: kafka-app
      image: my-kafka-app:latest
```

Optionally, a `preStop` hook can be used to delay termination and allow consumer group rebalancing to complete:

```yaml
lifecycle:
  preStop:
    exec:
      command: ["sh", "-c", "sleep 10"]
```

---

## Kafka Consumer – Spring Kafka

### Enable Graceful Shutdown

Spring Kafka automatically reacts to the `SIGTERM` signal sent by Kubernetes by stopping listener containers when the Spring context is closed.

To ensure proper behavior, configure the listener container:

```yaml
spring:
  kafka:
    listener:
      ack-mode: MANUAL
      graceful-shutdown:
        enabled: true
```

> ⚠️ Manual acknowledgment is recommended when message processing must complete before offsets are committed.

### Consumer Example

```java
@KafkaListener(
    topics = "orders",
    groupId = "order-consumers"
)
public void consume(
        ConsumerRecord<String, String> record,
        Acknowledgment acknowledgment
) {
    process(record);
    acknowledgment.acknowledge();
}
```

During shutdown:

* Spring stops polling for new records.
* In-flight records are processed.
* Offsets are committed before partitions are revoked.

---

## Kafka Producer – Spring Kafka

### Producer Configuration

Ensure the producer flushes buffered messages on shutdown:

```yaml
spring:
  kafka:
    producer:
      acks: all
      retries: 3
```

Spring Boot automatically closes the `KafkaTemplate` when the application context shuts down, which triggers a `flush()` on the underlying producer.

### Producer Example

```java
@Service
public class OrderProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public OrderProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendOrder(String orderId, String payload) {
        kafkaTemplate.send("orders", orderId, payload);
    }
}
```

---

## Handling Shutdown Explicitly (Optional)

For more control, you can hook into the Spring lifecycle:

```java
@Component
public class ShutdownHandler {

    private final KafkaTemplate<?, ?> kafkaTemplate;

    public ShutdownHandler(KafkaTemplate<?, ?> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @PreDestroy
    public void onShutdown() {
        kafkaTemplate.flush();
    }
}
```

---

## Key Takeaways

* **Pods are ephemeral** in EKS; shutdowns are expected and frequent.
* Spring Kafka integrates naturally with Kubernetes lifecycle events.
* Consumers must stop polling, finish processing, and commit offsets.
* Producers must flush pending records before termination.
* Proper graceful shutdown minimizes rebalances, duplicates, and message loss.
