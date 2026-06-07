/*
 * Copyright 2026; Réal Demers.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.rd.fullstack.springbooteda.util.kafka;

import java.util.Map;
import java.util.Set;

import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;

/**
 * Strategy interface for Kafka broker lifecycle management.
 * <p>
 * Two implementations are provided:
 * <ul>
 *   <li>{@link EmbeddedBrokerLifecycle} — starts an in-process
 *       {@code EmbeddedKafkaKraftBroker} (default; no external infrastructure).</li>
 *   <li>{@link ExternalBrokerLifecycle} — connects to an already-running
 *       external Kafka cluster (Docker, Confluent Cloud, MSK, …).</li>
 * </ul>
 * The correct implementation is selected automatically by
 * {@link KafkaSandbox.Builder#build()} depending on whether
 * {@link KafkaSandbox.Builder#bootstrapServers(String)} was called.
 * <p>
 * Package-private: not part of the public API of this library.
 */
interface BrokerLifecycle {

    /**
     * Starts the broker and synchronises all topics from the registry.
     *
     * @param cfg           sandbox configuration
     * @param topicRegistry topics to create / verify on startup
     * @throws IllegalStateException if the broker cannot be started or reached
     */
    void start(KafkaConfig cfg, Map<String, TopicConfig> topicRegistry);

    /**
     * Stops the broker and releases all underlying resources.
     * Implementations must be idempotent (safe to call more than once).
     */
    void stop();

    /**
     * Returns the Kafka bootstrap-servers connection string,
     * e.g. {@code "localhost:12345"} or {@code "kafka-host:9092"}.
     *
     * @throws IllegalStateException if the lifecycle has not been started
     */
    String getBootstrapServers();

    /** Returns {@code true} if the broker is currently started. */
    boolean isStarted();

    /**
     * Returns the set of topic names known to the broker.
     * Internal Kafka topics (e.g. {@code __consumer_offsets}) are <em>not</em>
     * filtered here; callers are responsible for filtering if needed.
     *
     * @throws Exception if the topic list cannot be retrieved
     */
    Set<String> getTopics() throws Exception;

    /**
     * Ensures the given topic (and its DLT, if configured) exists on the broker.
     * <p>
     * For embedded brokers this is a direct in-process call.
     * For external brokers this may involve an AdminClient {@code createTopics}
     * request (controlled by {@code autoCreateTopic}).
     *
     * @param topicConfig the topic to synchronise
     * @throws IllegalStateException if the topic is missing and creation is disabled
     */
    void syncTopic(TopicConfig topicConfig);

    /**
     * Returns broker-level JMX / metrics data.
     * <p>
     * Only supported by the embedded broker implementation.
     * Calling this method on {@link ExternalBrokerLifecycle} throws
     * {@link UnsupportedOperationException}.
     *
     * @throws UnsupportedOperationException if not supported by the implementation
     */
    default Map<MetricName, ? extends Metric> getBrokerMetrics() {
        throw new UnsupportedOperationException(
            "getBrokerMetrics() is not supported by this BrokerLifecycle implementation.");
    }
}
