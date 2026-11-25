# Classic Approach vs. Share Groups

> Part of the **Kafka Engineering Guide** of `org-rd-fullstack-springboot-eda`. See the [project README](../README.md).

**Scope:** Apache Kafka offers two consumption models that address different needs: the **classic Consumer Group** and the **Share Group**, introduced with the shared consumption protocol (KIP-932).

## Table of contents

- [Consumer](#consumer)
- [Classic Approach : Consumer Group](#classic-approach--consumer-group)
- [New Approach: Share Group (KIP-932)](#new-approach--share-group-kip-932)
- [Comparison Table](#comparison-table)
- [Impacts on EDA Architecture](#impacts-on-eda-architecture)
- [Architectural Summary](#architectural-summary)

## **Consumer**

The fundamental difference concerns how Kafka distributes messages and manages their processing.

- **Classic Consumer Group:** partitions are assigned to consumers. Each partition is consumed by only one consumer in the group at a time.
- **Share Group:** messages can be distributed among multiple consumers in the same group, even when they belong to a single partition.

## Classic Approach : Consumer Group

A partition is assigned to only one consumer at a time within the group. The classic Consumer Group prioritizes sequential partition reading, with progress management based on offsets.

**Characteristics:**

- One active consumer per partition and per group.
- Parallelism is limited by the number of partitions.
- Message order is preserved at the partition-reading level.
- Progress is tracked using committed offsets.
- A rebalance may be required when the distribution of consumers changes.

This model is particularly suitable for processing that requires a predictable reading order, business-state projections, and stream-processing applications.

## New Approach : Share Group (KIP-932)

Multiple consumers can process messages from the same partition. The Share Group prioritizes work distribution and parallelism, with individual tracking of messages.

**Characteristics:**

- Multiple consumers can share the same partition.
- Parallelism is no longer limited by the number of partitions.
- The overall processing order of messages is no longer guaranteed, even within a partition.
- Each message has its own processing state.
- Failures can result in individual messages being redistributed.
- Kafka counts delivery attempts to limit infinite retries.

This approach brings Kafka closer to a work-queue model, without turning its topics into traditional message queues.

## Comparison Table

| Criterion            | Consumer Group                   | Share Group                             |
| -------------------- | -------------------------------- | --------------------------------------- |
| Distribution unit    | Partition                        | Messages / batches                      |
| Partition assignment | Exclusive                        | Shared                                  |
| Parallelism          | Limited by partitions            | Independent of the number of partitions |
| Reading order        | Guaranteed per partition         | Not guaranteed across batches           |
| Progress tracking    | Offset commits                   | Individual acknowledgment               |
| Failure handling     | Replay from uncommitted offsets  | Redelivery of unacknowledged messages   |
| Problematic messages | Application-level handling       | Attempt counter and archiving           |
| Use cases            | Ordered streams, business states | Independent tasks, parallel processing  |

## Impacts on EDA Architecture

The choice between the two models should primarily depend on the business semantics of the events.

**Classic Consumer Group:** preferred when events represent a sequence of state changes whose order is important.

Example: order creation, payment, shipping, and cancellation.

**Share Group:** preferred when messages represent independent tasks that can be executed in any order.

Example: document generation, notification delivery, or parallel file processing.

One important point: **the Share Group does not eliminate the need for idempotency**. Delivery remains *at-least-once*. A failure after the business commit but before the Kafka acknowledgment can result in the message being delivered again.

## Architectural Summary

> The **classic Consumer Group** prioritizes the consistency and ordering of event streams, with parallelism determined by the partitions.
>
> The **Share Group** prioritizes dynamic distribution of processing, with parallelism independent of partitions, at the cost of losing guarantees about processing order.
>
> The two models are complementary: the former is better suited to processing ordered business events, while the latter addresses the needs of concurrent processing of independent tasks.

**Point of caution:** Share Groups first appeared as an early-access feature in Kafka 4.0, and then as a preview in Kafka 4.1. Before adopting it in production, verify the cluster version, the feature's actual availability, and its support by the Kafka platform being used.
