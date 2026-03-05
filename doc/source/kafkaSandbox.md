# KafkaSandbox

`KafkaSandbox` is a lightweight utility built on top of **Spring Kafka EmbeddedKafka** that provides a **clean, explicit, and safe API** for rugitnning Kafka in-memory during **unit and integration tests**.

It focuses on:

* correctness over cleverness
* explicit lifecycle management
* avoiding misleading generics
* first-class support for **transactional templates**
* first-class support for **transactional producers**

---

## ✨ Features

* 🚀 Embedded Kafka (KRaft-based)
* 🧪 Ideal for unit & integration tests
* 📦 Topic registry with explicit configuration
* 🔁 Producer and KafkaTemplate caching
* 🔐 Transactional producer support (`transactional.id`)
* 🎧 Programmatic consumer listeners
* 🧭 Consumer group offset control
* 🧼 Clean startup / shutdown lifecycle
* ❌ No fake type safety (honest use of `?,?` internally)

---

## 📦 Requirements

* Java 17+
* Spring Kafka (test scope)
* Apache Kafka Clients

---

## 🏗️ Core Concepts

### KafkaSandbox usage

The main entry point.
Manages:

* Embedded Kafka broker
* Topic lifecycle
* Producers & consumers
* Transactional semantics

KafkaSandbox can be used standalone or injected as a Spring bean
in test configurations.

---

### TopicConfig

An **immutable record** describing:

* topic metadata
* serializers / deserializers
* extra producer / consumer properties
* transactional behavior

> Anything that changes producer lifecycle (e.g. `transactional.id`) is
> **explicitly modeled**, not hidden in generic property maps.

---

## 🚀 Quick Start

### 1. Create and start the sandbox

```java
KafkaSandbox sandbox =
    KafkaSandbox.builder()
        .autoStart(true)
        .autoCreateTopic(false)

        .addTopic(
            "orders",
            3,
            (short) 1,
            StringSerializer.class,
            StringSerializer.class,
            StringDeserializer.class,
            StringDeserializer.class
        )

        .build();
```

---

## ✉️ Producing Messages

### Non-transactional producer

```java
KafkaTemplate<Object, Object> orders =
    sandbox.getKafkaTemplate("orders");

orders.send("orders", "order-1", "CREATED");
orders.send("orders", "order-2", "CONFIRMED");
```

---

### Transactional producer (KafkaTemplate)

```java
KafkaTemplate<Object, Object> payments =
    sandbox.getKafkaTemplate("payments", "transactional-id");

payments.executeInTransaction(kt -> {
    kt.send("payments", "payment-1", "INIT");
    kt.send("payments", "payment-1", "AUTHORIZED");
    return null;
});
```

If an exception is thrown inside `executeInTransaction`,
**no messages are committed**.

---

### Transactional producer (low-level API)

```java
Producer<Object, Object> producer =
    sandbox.getProducer("payments", "transactional-id");

try {
    producer.beginTransaction();

    producer.send(new ProducerRecord<>("payments", "p-1", "INIT"));
    producer.send(new ProducerRecord<>("payments", "p-1", "AUTHORIZED"));

    producer.commitTransaction();
} catch (Exception e) {
    producer.abortTransaction();
}
```

⚠️ The caller is responsible for providing a unique `transactional.id`
per logical producer lifecycle. Reusing the same id across concurrent
producers will result in producer fencing.

---

## 🎧 Consuming Messages

### Simple listener

```java
sandbox.setupMessageListener(
    "orders",
    (MessageListener<String, String>) record ->
        System.out.println("[ORDERS] " + record.value())
);
```

---

### Manual acknowledgment listener

```java
sandbox.setupMessageListener(
    "payments",
    (AcknowledgingMessageListener<String, String>) (record, ack) -> {
        System.out.println("[PAYMENTS] " + record.value());
        ack.acknowledge();
    }
);
```

KafkaSandbox configures consumers with:

* `isolation.level=read_committed`

This ensures aborted transactional records are never visible to listeners.

Listeners are managed and cached by the sandbox and are automatically
stopped and destroyed during shutdown.

---

## 🔁 Consumer Group Offset Control

```java
sandbox.seekConsumerGroupOffsets(
    "orders",
    "test-group",
    Map.of(
        0, 0,
        1, 10
    )
);
```

Useful for:

* replay scenarios
* deterministic tests
* failure simulations

---

## ⚙️ Configuration Model

### Why no generics internally?

Kafka serializers/deserializers are **runtime concerns**.
Java generics cannot enforce correctness at runtime.

Therefore:

* internal APIs use `?,?`
* generics are only exposed at **listener boundaries**

This avoids:

* unsafe casts
* false type safety
* misleading APIs

---

## 🔐 Transactional Support Design

Transactional behavior is modeled explicitly:

* transactional producers:

  * are never shared
  * are cached by `(topic + transactionalId)`
  * call `initTransactions()` exactly once

This prevents:

* producer fencing
* accidental sharing
* invalid configurations

---

## 🧼 Shutdown

Always shut down the sandbox to release resources:

```java
sandbox.stop();
```

This will:

* stop all listeners
* flush & close producers
* destroy the embedded broker

---

## 🧪 Usage in Tests (JUnit 5)

```java
class KafkaIntegrationTest {

    static KafkaSandbox sandbox =
        KafkaSandbox.builder()
            .autoStart(true)
            .addTopic(...)
            .build();

    @AfterAll
    static void shutdown() {
        sandbox.stop();
    }
}
```

---

## ❓ When to Use KafkaSandbox

✅ Good fit:

* unit tests
* integration tests
* transactional behavior validation
* consumer group simulations

❌ Not intended for:

* performance benchmarking
* production usage
* long-running environments

---

## 🧭 Design Principles

* Explicit over implicit
* Immutable configuration
* Lifecycle-aware resources
* Kafka semantics first
* No magic, no hidden state

---

## 📌 License

Apache License 2.0

---

## 🚀 Roadmap (Optional Ideas)

* transactional id uniqueness validation
* EOS consume → produce pipelines
* test utilities (assertions, await helpers)
* metrics & logging hooks

---
