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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link BrokerLifecycle} implementation that connects to an already-running
 * external Kafka cluster (local Docker Compose, Confluent Cloud, Amazon MSK, …).
 * <p>
 * On {@link #start}, the lifecycle:
 * <ol>
 *   <li>Opens a long-lived {@link AdminClient} for the provided bootstrap-servers.</li>
 *   <li>Performs a fast {@code describeCluster} call to verify connectivity and fail
 *       early if the cluster is unreachable.</li>
 *   <li>Synchronises every topic in the registry:
 *       <ul>
 *         <li>If the topic exists — nothing to do.</li>
 *         <li>If the topic does not exist and {@code autoCreateTopic = true} — creates it.</li>
 *         <li>If the topic does not exist and {@code autoCreateTopic = false} — throws
 *             {@link IllegalStateException}.</li>
 *       </ul>
 *   </li>
 * </ol>
 * The same {@link AdminClient} remains open until {@link #stop()} so that topics
 * added after start (e.g. the default-DLT) can also be synchronised on demand.
 * <p>
 * {@link #getBrokerMetrics()} is <b>not</b> supported and throws
 * {@link UnsupportedOperationException} (inherited from the interface default).
 * <p>
 * Package-private: not part of the public API of this library.
 */
class ExternalBrokerLifecycle implements BrokerLifecycle {

    private static final Logger logger =
            LoggerFactory.getLogger(ExternalBrokerLifecycle.class);

    private final String  bootstrapServers;
    private final boolean autoCreateTopic;
    private AdminClient   adminClient;

    ExternalBrokerLifecycle(String bootstrapServers, boolean autoCreateTopic) {
        if (bootstrapServers == null || bootstrapServers.isBlank())
            throw new IllegalArgumentException("bootstrapServers must not be blank.");

        this.bootstrapServers = bootstrapServers;
        this.autoCreateTopic  = autoCreateTopic;
        this.adminClient      = null;
    }

    @Override
    public synchronized void start(KafkaConfig cfg, Map<String, TopicConfig> topicRegistry) {
        adminClient = createAdminClient();

        // Fail-fast: verify the cluster is reachable before proceeding.
        try {
            String clusterId = adminClient.describeCluster().clusterId().get();
            logger.info("ExternalBrokerLifecycle connected to Kafka cluster: {}.", clusterId);
        } catch (Exception ex1) {
            try {
                adminClient.close(Duration.ofSeconds(KafkaConstants.CST_ADMIN_CLOSE_TIMEOUT_SECONDS));
            } catch (Exception ex2) {
                logger.error("adminClient.close:", ex2);
            } finally { 
                adminClient = null; 
            }

            throw new IllegalStateException(
                "Cannot connect to external Kafka cluster at: " + bootstrapServers, ex1);
        }

        try {
            if (!topicRegistry.isEmpty()) {
                topicRegistry.values().forEach(this::syncTopic);
                logger.info("Topics synchronised with external broker: {}.", topicRegistry.keySet());
            }
        } catch (RuntimeException ex1) {
            try {
                adminClient.close(Duration.ofSeconds(KafkaConstants.CST_ADMIN_CLOSE_TIMEOUT_SECONDS));
            } catch (Exception ex2) {
                logger.warn("Exception closing ExternalBrokerLifecycle AdminClient:", ex2);
            } finally { 
                adminClient = null; 
            }

            throw ex1;
        }
    }

    @Override
    public synchronized void stop() {
        if (!isStarted())
            return;

        try {
            adminClient.close(Duration.ofSeconds(KafkaConstants.CST_ADMIN_CLOSE_TIMEOUT_SECONDS));
        } catch (Exception ex) {
            logger.warn("Exception closing ExternalBrokerLifecycle AdminClient:", ex);
        } finally {
            // A closed AdminClient must not keep the lifecycle marked as started. Clearing the
            // reference allows KafkaSandbox.start() to create a fresh client after a stop().
            adminClient = null;
        }
    }

    @Override
    public String getBootstrapServers() {
        requireStarted();
        return bootstrapServers;
    }

    @Override
    public Set<String> getTopics() throws Exception {
        requireStarted();
        return adminClient.listTopics().names().get();
    }

    @Override
    public void syncTopic(TopicConfig topicConfig) {
        requireStarted();
        Objects.requireNonNull(topicConfig,"TopicConfig must not be null.");

        try {
            Set<String> existingTopics = adminClient.listTopics().names().get();
            List<NewTopic> toCreate = new ArrayList<>();

            if (!existingTopics.contains(topicConfig.name())) {
                if (!autoCreateTopic)
                    throw new IllegalStateException("Topic '" + topicConfig.name() + 
                                                    "' does not exist on the external broker " +
                                                    "and autoCreateTopic is false.");
                toCreate.add(new NewTopic(topicConfig.name(), topicConfig.partitions(), topicConfig.replicas()));
            }

            if (!topicConfig.dltName().isEmpty() && !existingTopics.contains(topicConfig.dltName())) {
                if (!autoCreateTopic)
                    throw new IllegalStateException(
                        "DLT topic '" + topicConfig.dltName() + "' does not exist on the external broker " +
                        "and autoCreateTopic is false.");
                toCreate.add(
                    new NewTopic(topicConfig.dltName(), topicConfig.partitions(), topicConfig.replicas()));
            }

            if (!toCreate.isEmpty()) {
                adminClient.createTopics(toCreate).all().get();
                toCreate.forEach(t -> logger.info("Created topic '{}' on external broker.", t.name()));
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to synchronise topic '" + topicConfig.name() + 
                "' with external broker.", ex);
        }
    }

    @Override
    public boolean isStarted()  {
        return (adminClient != null);
    }
    
    @Override 
    public void requireStarted() {
        if (adminClient == null)
            throw new IllegalStateException("ExternalBrokerLifecycle is not started.");
    }
    
    @Override
    public AdminClient getAdminClient()  {
        requireStarted();
        return adminClient;
    }   

    private AdminClient createAdminClient() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,      bootstrapServers);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG,     KafkaConstants.CST_REQUEST_TIMEOUT_MS_CONFIG);
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, KafkaConstants.CST_MAX_BLOCK_MS_CONFIG);
        return AdminClient.create(props);
    }
}
