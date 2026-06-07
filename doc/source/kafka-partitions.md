# Partitions, Consumers, and Ordering Guarantees

Apache Kafka is designed as a distributed, scalable, and fault-tolerant event streaming platform. To achieve these properties, Kafka introduces the concept of topics partitioned into multiple partitions, which directly influences how messages are processed, scaled, and ordered.

## Partitions as the Unit of Parallelism

A Kafka topic is split into partitions, and each partition is an ordered, immutable log of records. Kafka guarantees that records within a single partition are delivered to consumers **in the exact order in which they were written**. However, this ordering guarantee does **not extend across partitions**. The following figure illustrates partitioning with Kafka.

![Kafka partitions](../doc/asserts/EDA-Kafka-Partitions.png)

Partitions are also the fundamental unit of **parallelism and scalability**. Multiple partitions allow Kafka to distribute data across brokers and enable consumers to process records in parallel. The partition to which a message is written is determined by its partition key. Messages with the same key are always routed to the same partition, ensuring ordered processing for that key.

## Ordering Constraints and Business Semantics

Ordering is often a business requirement, not merely a technical concern. Consider an inventory management scenario in which events represent updates to a product’s stock level:

* Product Created
* Product StockIncreased
* Product StockDecreased
* Product Discontinued

These events must be processed in strict order for each inventory item. If a `Product StockDecreased` event is processed before `Product Created`, or after `Product Discontinued`, the system’s **state becomes inconsistent** and no longer reflects the actual inventory.

Kafka can guarantee this ordering only if all events related to the same product share the same partition key (e.g., `product_id`) and therefore land in the same partition. If events are distributed across partitions—either due to inconsistent keys or multiple topics—ordering is no longer guaranteed, and **consumers must implement complex compensation or reconciliation logic**.

## The Impact of Poor Partitioning

When partitioning is poorly designed:

* Messages related to the same entity may be processed concurrently by different consumer instances.
* Ordering guarantees are lost, leading to race conditions and data corruption.
* Scaling consumers horizontally becomes risky, as increased parallelism can break business invariants.

In distributed environments like EKS, where pods can be rescheduled, restarted, or scaled dynamically, these risks are amplified. Without a clear partitioning strategy aligned with business entities, **safe horizontal scaling becomes extremely difficult**. See here for more information about EKS : [Distribution - Scale and shutdown](./dist_scale_and_shutdown.md).

## Key Takeaway

In Kafka-based systems, partitioning strategy is an architectural decision, not an implementation detail. It defines:

* How the system scales.
* How consumers are deployed and parallelized.
* Whether business-critical ordering guarantees can be upheld.

Understanding the relationship between partitions, consumer processes, threads, and business semantics is essential to designing reliable, scalable, and correct event-driven systems.

### Web application (DEMO)

The demonstration web application enables users to experiment with partition keys and to analyze their impact on consumers.

![Web application partitions](../doc/asserts/WEB-Application-Partitions.png)
