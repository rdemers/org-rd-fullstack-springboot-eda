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

import org.apache.kafka.clients.admin.AdminClient;
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

    void start(KafkaConfig cfg, Map<String, TopicConfig> topicRegistry);
    void stop();

    String getBootstrapServers();

    boolean isStarted();
    void    requireStarted();

    AdminClient getAdminClient();

    Set<String> getTopics() throws Exception;
    void syncTopic(TopicConfig topicConfig);

    default Map<MetricName, ? extends Metric> getBrokerMetrics() {
        throw new UnsupportedOperationException(
            "getBrokerMetrics() is not supported by this BrokerLifecycle implementation.");
    }
}
