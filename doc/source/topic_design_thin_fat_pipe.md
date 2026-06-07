# Topic Design – Thin vs. Fat Pipe

## Introduction

Whether you are already experienced with Kafka or just beginning your first exploration, one question almost inevitably arises: should a topic be used to carry more than one event type ?

As with most architectural decisions, the answer is, of course, *“it depends.”* To better understand what best fits your requirements, it is useful to first examine the two extreme approaches. Doing so helps highlight the trade-offs involved and clarifies which behaviors and constraints are most important to your system.

---

## The Single Topic (Fat Pipe)

*One topic to rule them all.*

In this approach, all events—regardless of type or domain—are published to a single topic. While this might appear simple at first, it is rarely the right choice for production systems.

### Focus

Each consumer typically represents a service, and a fundamental principle of good service design is **focus**: a service should do one thing and do it well. With a single-topic approach, every service consumes all events, even those outside its core responsibility. This broad visibility becomes a source of temptation and increases the risk of services gradually taking on unintended responsibilities, ultimately eroding domain boundaries.

### Resource Wastage

Consuming all events means that services are activated far more often than necessary. Even if a service merely discards irrelevant messages, it still incurs processing overhead. This inefficiency is not limited to the consumer; **network bandwidth, broker I/O, and storage throughput** are also impacted by the unnecessary delivery of messages to uninterested consumers.

### Event and Schema Validation

When many event types coexist on the same topic, schema validation becomes significantly more complex. Each event may embed its schema, or reference one in a schema registry, but in either case, managing **multiple unrelated schemas per topic** weakens the semantic meaning of the topic itself. A topic with no clear contract or intent becomes harder to reason about, evolve, and govern.

### System Understandability

From an operational and architectural perspective, a single topic reduces system clarity. When all events flow through one pipe, it becomes difficult to understand which events exist, who produces them, and which services depend on them. Decommissioning or evolving event producers becomes risky, as identifying downstream consumers is no longer straightforward.

### Partition Key Complexity and Ordering Challenges

In a fat pipe approach, where a single topic is used to carry many unrelated event types, defining an effective partition key becomes one of the most critical—and often unsolvable—problems.

Kafka relies on the partition key to distribute records across partitions while preserving ordering guarantees. However, when a topic contains heterogeneous events spanning multiple domains, there is rarely a single, meaningful key that applies consistently to all event types. Some events may naturally align with a customer identifier, others with an order ID, a payment ID, or no obvious business key at all.

As a result, teams are often forced into suboptimal compromises:

* Using a generic or artificial key, which leads to uneven data distribution and hot partitions.
* Leaving the key unset, which allows Kafka to distribute records randomly, but eliminates any meaningful ordering guarantees.
* Reusing a key that only makes sense for a subset of events, which introduces false coupling between unrelated event streams.

This ambiguity directly impacts parallelism and scalability. Kafka consumers scale by processing partitions in parallel, typically across multiple processes or threads. When partitioning does not reflect a clear domain model, it becomes difficult—if not impossible—to distribute the workload efficiently. Some partitions may become overloaded, while others remain underutilized, reducing throughput and increasing latency.

Moreover, ordering semantics break down in a fat pipe scenario. Ordering in Kafka is guaranteed only within a single partition. When unrelated events share a topic and are spread across partitions using inconsistent keys, reconstructing a meaningful order becomes infeasible. Even when ordering is required only for specific entity types, the shared topic makes it hard to isolate and enforce those guarantees without complex, error-prone logic in consumers.

In practice, this leads to systems that are:

* Hard to scale horizontally
* Difficult to reason about
* Fragile in the face of change
* Highly dependent on consumer-side complexity to compensate for poor topic design

Ultimately, the inability to define a clear and stable partitioning strategy is one of the strongest arguments against the fat pipe model in Kafka-based architectures.

---

## Discrete Topics (Thin Pipes)

*Divide and conquer.*

At the opposite extreme, each event type is published to its own topic. At first glance, this seems like a clean and intuitive approach: consumers explicitly choose which events they want to consume. However, this model also comes with trade-offs.

### Ordering of Events

Kafka guarantees ordering only within a single partition. To preserve ordering, related events must share both a topic and a partition key. When events are split across multiple topics—or across multiple partitions—ordering guarantees no longer hold.

In practice, this can lead to scenarios where logically related events arrive in an unexpected or inconsistent order. While workarounds exist (such as buffering, correlation, or sequence numbers), they introduce additional complexity that is best avoided when possible. That said, if ordering is not a requirement for a given use case, this concern may be irrelevant.

### Performance and Scale

Kafka does not impose a hard limit on the number of topics, but topics, partitions, and brokers are closely related. Operational guidelines typically recommend not exceeding roughly 4,000 partitions per broker. In large-scale systems, excessive topic and partition counts can increase metadata overhead, recovery times, and operational complexity. If you expect to approach these limits, consolidating topics becomes an important consideration.

---

## Finding the Middle Ground

Examining the extremes helps identify what truly matters. In practice, the severity of the drawbacks described above depends heavily on the system’s scale, domain complexity, and operational constraints. Some perceived downsides may be acceptable trade-offs in a given context.

From the discussion so far, several desired qualities emerge:

### Focus, Understandability, Testability, and Maintainability

Efficiency is also a desirable goal when it can be achieved without sacrificing clarity. These qualities generally push us toward using more than one topic, but not necessarily one topic per event type. Two characteristics tend to influence the decision more than most: ordering and data security.

#### Ordering

Ordering is often a domain-specific requirement. Changes to the same entity usually need to occur in a well-defined sequence—for example, an order must be created before it can be cancelled. In contrast, there is often little value in enforcing ordering across unrelated entities. An order cancellation has no intrinsic relationship to an email address update, so these events naturally belong in different topics.

A common and effective strategy is to group events by aggregate or business entity, ensuring ordering where it matters while avoiding unnecessary coupling elsewhere.

#### Data Security

Data sensitivity can also influence topic design. Some events may contain confidential or regulated information that should only be visible to a limited set of consumers. For example, a payment event may need to communicate a state change (e.g., *completed* or *failed*) without exposing full payment details.

This concern is often best addressed through service and event design: publishing only the data that consumers actually need, enforcing topic-level ACLs, and avoiding the temptation to use topics as broad data-sharing channels.

---

## Summing Up

There is no single correct answer. The right approach depends on system size, scale, domain boundaries, ordering requirements, and data visibility constraints. In practice, most real-world architectures adopt a hybrid approach, combining shared topics for closely related events with more discrete topics where isolation, clarity, or security is required.

The key is to treat topics as intentional contracts, not just technical pipes, and to design them in a way that supports both current needs and future evolution.
