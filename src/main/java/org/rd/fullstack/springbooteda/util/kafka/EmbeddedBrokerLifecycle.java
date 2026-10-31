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

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

import kafka.server.BrokerServer;

/**
 * {@link BrokerLifecycle} implementation backed by a Spring-Kafka
 * {@link EmbeddedKafkaKraftBroker} (in-process, KRaft mode).
 * <p>
 * This is the default implementation used when no external bootstrap-servers
 * URL is provided to {@link KafkaSandbox.Builder}.  It is ideal for local
 * development and integration tests because no external Kafka infrastructure
 * is required.
 * <p>
 * <b>Note on internal API usage:</b> {@link #getBrokerMetrics()} relies on
 * the Kafka internal class {@code kafka.server.BrokerServer}.  This API is
 * not guaranteed to be stable across Kafka versions.
 * <p>
 * Package-private: not part of the public API of this library.
 */
class EmbeddedBrokerLifecycle implements BrokerLifecycle {

    private static final Logger logger =
            LoggerFactory.getLogger(EmbeddedBrokerLifecycle.class);

    private EmbeddedKafkaKraftBroker broker;
    private AdminClient              adminClient;

    public EmbeddedBrokerLifecycle() {
        super();
        broker = null;
        adminClient = null;
    }

    @Override
    public synchronized void start(KafkaConfig cfg, Map<String, TopicConfig> topicRegistry) {
        if (isStarted())
            return;

        EmbeddedKafkaKraftBroker broker =
                new EmbeddedKafkaKraftBroker(cfg.clusters(), cfg.clusterPartitions());

        broker.brokerProperties(buildBrokerProperties(cfg));
        broker.afterPropertiesSet();
        this.broker = broker;

        if (!topicRegistry.isEmpty()) {
            topicRegistry.values().forEach(this::syncTopic);
            logger.info("Topics created on embedded broker: {}.", topicRegistry.keySet());
        }
    }

    @Override
    public synchronized void stop() {
        if (!isStarted())
            return;
      
        try {
            broker.destroy();
            adminClient.close(Duration.ofSeconds(KafkaConstants.CST_ADMIN_CLOSE_TIMEOUT_SECONDS));
        } catch (Exception ex) {
            logger.warn("Exception destroying embedded Kafka broker:", ex);
        } finally {
            broker = null;
            adminClient = null;
        }
    }

    @Override
    public String getBootstrapServers() {
        requireStarted();
        return broker.getBrokersAsString();
    }

    @Override
    public Set<String> getTopics() {
        requireStarted();
        return broker.getTopics();
    }

    @Override
    public void syncTopic(TopicConfig topicConfig) {
        
        requireStarted();
        Objects.requireNonNull(topicConfig, "TopicConfig must not be null.");
        if (! isStarted())
            throw new IllegalStateException("EmbeddedBrokerLifecycle is not started.");

        final String topicName = topicConfig.name();
        final String dltName   = topicConfig.dltName();

        // Create the main topic if it does not exist.
        if (!broker.getTopics().contains(topicName)) {
            broker.addTopics(new NewTopic(topicName,
                             topicConfig.partitions(),
                             topicConfig.replicas()));
        }

        // Create the DLT if it is configured and does not exist.
        if (dltName != null && !dltName.isBlank() && 
            !broker.getTopics().contains(dltName)) {
            broker.addTopics(new NewTopic(dltName,
                             topicConfig.partitions(),
                             topicConfig.replicas()));
        }
    }

    @Override
    public Map<MetricName, ? extends Metric> getBrokerMetrics() {
        requireStarted();
        Map<Integer, BrokerServer> brokers = broker.getCluster().brokers();
        if (brokers.isEmpty())
            return Collections.emptyMap();

        return brokers.values().iterator().next().metrics().metrics();
    }

    @Override
    public boolean isStarted() {
        return broker != null;
    }

    @Override
    public void requireStarted() {
        if (broker == null)
            throw new IllegalStateException("EnbeddedBrokerLifecycle is not started.");
    }

    @Override
    public synchronized AdminClient getAdminClient()  {
        requireStarted();

        if (adminClient != null)
            return adminClient;

        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,  getBootstrapServers());
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, KafkaConstants.CST_REQUEST_TIMEOUT_MS_CONFIG);
        this.adminClient = AdminClient.create(props);
        
        return adminClient;
    }   

    private Map<String, String> buildBrokerProperties(KafkaConfig cfg) {
        return Map.of(
            // "cluster.id" is auto-generated by EmbeddedKafkaKraftBroker — do not set.
            "auto.create.topics.enable",                  String.valueOf(cfg.autoCreateTopic()),
            "log.retention.hours",                        "1",
            "transaction.state.log.replication.factor",   "1",
            "transaction.state.log.min.isr",              "1",
            "offsets.topic.replication.factor",           "1"
        );
    }
}
