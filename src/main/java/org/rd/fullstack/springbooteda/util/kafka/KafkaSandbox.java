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
 *
 * KafkaSandbox
 * ------------
 * A self-contained Kafka sandbox for local development and integration tests.
 * By default it starts an in-process EmbeddedKafkaKraftBroker (no external
 * infrastructure needed).  When bootstrapServers() is supplied to the builder
 * it connects to an already-running external cluster instead.
 *
 * Broker selection is handled by the BrokerLifecycle strategy:
 *   EmbeddedBrokerLifecycle — in-process KRaft broker (default).
 *   ExternalBrokerLifecycle — external cluster (Docker, Confluent Cloud, MSK…).
 */
package org.rd.fullstack.springbooteda.util.kafka;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.GroupListing;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.RecordsToDelete;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.GroupType;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.AcknowledgingMessageListener;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.GenericMessageListener;
import org.springframework.kafka.listener.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class KafkaSandbox extends AbstractTopicHandler<KafkaSandbox> implements AutoCloseable {
    private static final Logger logger =
            LoggerFactory.getLogger(KafkaSandbox.class);

    // To be clear ... Next release. See DLT setup.
    //private static final EnumSet<AckMode> TRANSACTIONAL_ACK_MODES =
    // EnumSet.of(AckMode.MANUAL_IMMEDIATE, AckMode.MANUAL);

    // Strategy: selects embedded or external broker at build() time.
    private final BrokerLifecycle brokerLifecycle;

    // Primary started-state guard.  Written inside synchronized blocks;
    // read (lock-free) from requireStarted(), isStarted(), and toString().
    private final AtomicBoolean started = new AtomicBoolean(false);

    private final KafkaConfig cfg;
    private AdminClient kAdminClient;    // Guarded by synchronized methods.

    // Note: Caches grow unbounded throughout the lifetime of this sandbox instance.
    //       This should not be an issue, as cached items remain in memory
    //       for the lifetime of the process.
    //       During shutdown, ensure that all resources are properly released. 
    private final Map<ErrorHandleCacheKey, DefaultErrorHandler>                   errorHandlerCache;
    private final Map<ProducerCacheKey, Producer<?, ?>>                           producerCache;
    private final Map<TemplateCacheKey, KafkaTemplate<?, ?>>                      templateCache;
    private final Map<ConsumerCacheKey, KafkaConsumer<?, ?>>                      consumerCache;
    private final Map<ListenerCacheKey, ConcurrentMessageListenerContainer<?, ?>> listenerCache;

    private final Set<ConsumerGroupMonitor> monitorRegistry;

    private record ErrorHandleCacheKey(String topicName) {}
    private record ProducerCacheKey(String topicName, String suffix) {}
    private record TemplateCacheKey(String topicName, String suffix) {}
    private record ConsumerCacheKey(String groupId, String topicName) {}
    private record ListenerCacheKey(int concurrency, String groupId, String topicName) {}

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractTopicHandler<Builder> {
        private int clusters          = KafkaConstants.CST_NBR_CLUSTERS_DEFAULT;
        private int clusterPartitions = KafkaConstants.CST_NBR_CLUSTERS_PARTITIONS_DEFAULT;
        private int concurrency       = KafkaConstants.CST_NBR_CONCURRENCY_DEFAULT;

        private boolean autoCreateTopic          = false;
        private boolean autoStart                = false;
        private String  externalBootstrapServers = null; // null → use embedded broker.

        /**
         * @deprecated Due to a bug in the current version of EmbeddedKafkaKraftBroker, only a single
         * cluster is supported. This method is a no-op and will be re-enabled in a future release.
         */
        @Deprecated
        public Builder clusters(int clusters) {
            if (clusters != 1) {
                logger.warn("Due to an existing bug, only 1 cluster is supported. Ignoring the value {}.", clusters);
            }
            // Do nothing, as it's forced to 1.
            return this;
        }

        public Builder clusterPartitions(int clusterPartitions) {
            this.clusterPartitions = clusterPartitions;
            return this;
        }

        public Builder concurrency(int concurrency) {
            this.concurrency = concurrency;
            return this;
        }

        public Builder autoStart(boolean autoStart) {
            this.autoStart = autoStart;
            return this;
        }

        public Builder autoCreateTopic(boolean autoCreateTopic) {
            this.autoCreateTopic = autoCreateTopic;
            return this;
        }

        /**
         * Connects to an external Kafka cluster instead of starting an embedded broker.
         * <p>
         * When this is set, {@link EmbeddedBrokerLifecycle} is replaced by
         * {@link ExternalBrokerLifecycle}.  The {@code clusterPartitions} parameter
         * is ignored for external brokers.
         *
         * @param url bootstrap-servers string, e.g. {@code "kafka-host:9092"}
         */
        public Builder bootstrapServers(String url) {
            Objects.requireNonNull(url, "bootstrapServers must not be null.");
            this.externalBootstrapServers = url;
            return this;
        }

        public KafkaSandbox build() {
            KafkaConfig cfg = new KafkaConfig(
                UUID.randomUUID(), clusters, clusterPartitions, concurrency, autoCreateTopic
            );

            BrokerLifecycle lifecycle = (externalBootstrapServers != null)
                ? new ExternalBrokerLifecycle(externalBootstrapServers, autoCreateTopic)
                : new EmbeddedBrokerLifecycle();

            KafkaSandbox kafkaSandbox = new KafkaSandbox(cfg, lifecycle, getTopicRegistry());
            if (autoStart)
                kafkaSandbox.start();

            return kafkaSandbox;
        }

        @Override
        public void preProcess(TopicConfig topicConfig) { /* Nothing to do in the builder. */ }

        @Override
        public void postProcess(TopicConfig topicConfig) { /* Nothing to do in the builder. */ }
    }

    private KafkaSandbox(KafkaConfig cfg, BrokerLifecycle brokerLifecycle,
                         Map<String, TopicConfig> topicRegistry) {
        super(topicRegistry);
        this.cfg             = Objects.requireNonNull(cfg, "KafkaConfig must not be null.");
        this.brokerLifecycle = Objects.requireNonNull(brokerLifecycle, "BrokerLifecycle must not be null.");

        this.kAdminClient  = null;

        this.errorHandlerCache = new ConcurrentHashMap<>();
        this.producerCache     = new ConcurrentHashMap<>();
        this.templateCache     = new ConcurrentHashMap<>();
        this.consumerCache     = new ConcurrentHashMap<>();
        this.listenerCache     = new ConcurrentHashMap<>();
        this.monitorRegistry   = ConcurrentHashMap.newKeySet();
    }

    public synchronized void start() {
        if (started.get())
            return;

        brokerLifecycle.start(cfg, getTopicRegistry());
        started.set(true);
        logger.info("KafkaSandbox started on: {}.", brokerLifecycle.getBootstrapServers());
    }

    public void stop() {

        final List<ConsumerGroupMonitor>                     monitorsSnapshot;
        final List<KafkaConsumer<?, ?>>                      consumersSnapshot;
        final List<ConcurrentMessageListenerContainer<?, ?>> listenersSnapshot;
        final List<KafkaTemplate<?, ?>>                      templatesSnapshot;
        final List<Producer<?, ?>>                           producersSnapshot;
        final AdminClient                                    adminClientSnapshot;

        synchronized (this) {
            if (!started.getAndSet(false))
                return;

            monitorsSnapshot  = new ArrayList<>(monitorRegistry);
            consumersSnapshot = new ArrayList<>(consumerCache.values());
            listenersSnapshot = new ArrayList<>(listenerCache.values());
            templatesSnapshot = new ArrayList<>(templateCache.values());
            producersSnapshot = new ArrayList<>(producerCache.values());
            adminClientSnapshot = kAdminClient;

            kAdminClient  = null;
            errorHandlerCache.clear();
            monitorRegistry.clear();
            consumerCache.clear();
            listenerCache.clear();
            templateCache.clear();
            producerCache.clear();
        }

        monitorsSnapshot.forEach(m -> { shutdown(m); });
        consumersSnapshot.forEach(cs -> { shutdown(cs); });
        listenersSnapshot.forEach(lc -> { shutdown(lc); });
        templatesSnapshot.forEach(tc -> { shutdown(tc); });
        producersSnapshot.forEach(pc -> { shutdown(pc); });

        if (adminClientSnapshot != null) {
            try {
                adminClientSnapshot.close(Duration.ofSeconds(KafkaConstants.CST_ADMIN_CLOSE_TIMEOUT_SECONDS));
            } catch (Exception ex) {
                logger.warn("Exception closing AdminClient: {}.", ex.getMessage());
            }
        }

        brokerLifecycle.stop();
        logger.info("KafkaSandbox stopped and all resources released.");
    }

    @Override
    public void close() {
        stop();
    }

    public <K, V> Producer<K, V> getProducer(String topicName) {
        return getProducer(topicName, false);
    }

    @SuppressWarnings("unchecked")
    public <K, V> Producer<K, V> getProducer(String topicName, boolean indTrxFlag) {
        requireStarted();
        Objects.requireNonNull(topicName, "topicName must not be null.");

        final ProducerCacheKey key =
            new ProducerCacheKey(topicName,
                (indTrxFlag ? KafkaConstants.CST_TRX_TRX_POSTFIX : KafkaConstants.CST_TRX_NO_TRX_POSTFIX));

        // Fast path: return the cached producer without acquiring the sandbox monitor.
        Producer<?, ?> cached = producerCache.get(key);
        if (cached != null)
            return (Producer<K, V>) cached;

        final TopicConfig topicConfig = getTopicConfig(topicName);
        Producer<K, V> created =
            new KafkaProducer<>(buildProducerConfig(topicConfig,
                (indTrxFlag ? Optional.of(getUniqueTrxID()) : Optional.empty())));
        if (indTrxFlag) {
            try {
                created.initTransactions();
            } catch (RuntimeException ex) {
                created.close(Duration.ZERO);
                throw ex;
            }
        }

        // Publish atomically; if another thread won the race for this key, discard ours.
        Producer<?, ?> previous = producerCache.putIfAbsent(key, created);
        if (previous != null) {
            shutdown(created);
            return (Producer<K, V>) previous;
        }

        // If the sandbox was stopped concurrently, do not leak the freshly created producer
        // (stop() snapshots the cache, so a producer inserted afterwards would be orphaned).
        if (!started.get() && producerCache.remove(key, created)) {
            shutdown(created);
            throw new IllegalStateException("KafkaSandbox was stopped during producer creation.");
        }
        return created;
    }

    public <K, V> KafkaTemplate<K, V> getKafkaTemplate(String topicName) {
        return getKafkaTemplate(topicName, false);
    }

    @SuppressWarnings("unchecked")
    public <K, V> KafkaTemplate<K, V> getKafkaTemplate(String topicName, boolean indTrxFlag) {
        requireStarted();
        Objects.requireNonNull(topicName, "topicName must not be null.");

        final TemplateCacheKey key =
            new TemplateCacheKey(topicName,
                (indTrxFlag ? KafkaConstants.CST_TRX_TRX_POSTFIX : KafkaConstants.CST_TRX_NO_TRX_POSTFIX));

        KafkaTemplate<?, ?> cached = templateCache.get(key);
        if (cached != null)
            return (KafkaTemplate<K, V>) cached;

        final TopicConfig cfg = getTopicConfig(topicName);
        ProducerFactory<K, V> pf = buildProducerFactory(cfg,
            (indTrxFlag ? Optional.of(getUniqueTrxID()) : Optional.empty()));

        KafkaTemplate<K, V> template = new KafkaTemplate<>(pf);
        template.setDefaultTopic(topicName);

        // Publish atomically; if another thread won the race for this key, tear ours down.
        KafkaTemplate<?, ?> previous = templateCache.putIfAbsent(key, template);
        if (previous != null) {
            shutdown(template);
            return (KafkaTemplate<K, V>)previous;
        }

        // If the sandbox was stopped concurrently, do not leak a running container
        // (stop() snapshots the cache, so a container inserted afterwards would be orphaned).
        if (!started.get() && templateCache.remove(key, template)) {
            shutdown(template);
            throw new IllegalStateException("KafkaSandbox was stopped during a template creation.");
        }
        return template;
    }

    public <K, V> KafkaConsumer<K, V> getConsumer() {
        return getConsumer(null, null);
    }

    @SuppressWarnings("unchecked")
    public <K, V> KafkaConsumer<K, V> getConsumer(String group, String topicName) {
        requireStarted();
        Objects.requireNonNull(topicName, "topicName must not be null.");

        final ConsumerCacheKey key = new ConsumerCacheKey(group, topicName);
        KafkaConsumer<?, ?> cached = consumerCache.get(key);
        if (cached != null)
            return (KafkaConsumer<K, V>) cached;
        
        TopicConfig cfg = getTopicConfig(topicName);
        KafkaConsumer<K, V> consumer = new KafkaConsumer<>(buildConsumerConfig(group, cfg));

        // Publish atomically; if another thread won the race for this key, tear ours down.
        KafkaConsumer<?, ?> previous = consumerCache.putIfAbsent(key, consumer);
        if (previous != null) {
            shutdown(consumer);
            return (KafkaConsumer<K, V>)previous;
        }

        // If the sandbox was stopped concurrently, do not leak a running consumer.
        if (!started.get() && consumerCache.remove(key, consumer)) {
            shutdown(consumer);
            throw new IllegalStateException("KafkaSandbox was stopped during a consumer creation.");
        }
        return consumer;
    }
    
    public <K, V> void setupMessageListener(String topicName, MessageListener<K, V> listener) {
        setupMessageListener(cfg.concurrency(), cfg.uuidID().toString(), topicName, AckMode.MANUAL_IMMEDIATE, listener);
    }

    public <K, V> void setupMessageListener(String topicName, String groupId, MessageListener<K, V> listener) {
        setupMessageListener(cfg.concurrency(), groupId, topicName, AckMode.MANUAL_IMMEDIATE, listener);
    }

    public <K, V> void setupMessageListener(String topicName, String groupId, AcknowledgingMessageListener<K, V> listener) {
        setupMessageListener(cfg.concurrency(), groupId, topicName, AckMode.MANUAL_IMMEDIATE, listener);
    }

    public <K, V> void setupMessageListener(String topicName, AcknowledgingMessageListener<K, V> listener) {
        setupMessageListener(cfg.concurrency(), cfg.uuidID().toString(), topicName, AckMode.MANUAL_IMMEDIATE, listener);
    }

    public <K, V> void setupMessageListener(int concurrency, String groupId, String topicName,
                                            AckMode ackMode, GenericMessageListener<ConsumerRecord<K, V>> listener) {
        requireStarted();
        Objects.requireNonNull(topicName, "topicName must not be null.");
        Objects.requireNonNull(groupId,   "groupId must not be null.");
        Objects.requireNonNull(ackMode,   "ackMode must not be null.");
        Objects.requireNonNull(listener,  "Listener must not be null.");
        if (groupId.isEmpty() || topicName.isEmpty() || concurrency <= 0)
            throw new IllegalArgumentException("Invalid parameters for listener setup.");

        final ListenerCacheKey key = new ListenerCacheKey(concurrency, groupId, topicName);

        // Fast path: a listener already exists for this (concurrency, group, topic).
        if (listenerCache.containsKey(key))
            return;

        TopicConfig cfg = getTopicConfig(topicName);
        if (concurrency > cfg.partitions())
            logger.warn("The concurrent threads requested is greater than the partitions (concurrency: {}, partitions: {}).",
                         concurrency, cfg.partitions());

        ContainerProperties props = new ContainerProperties(topicName);
        props.setAckMode(ackMode);

        ConcurrentMessageListenerContainer<K, V> container =
            new ConcurrentMessageListenerContainer<>(
                new DefaultKafkaConsumerFactory<>(buildConsumerConfig(groupId, cfg)), props);

        container.setCommonErrorHandler(getErrorHandler(topicName)); 
        container.setupMessageListener(listener);
        container.setConcurrency(concurrency);

        // start() blocks on partition assignment / a broker round-trip; run it OUTSIDE any
        // lock so a slow broker cannot stall other synchronized sandbox operations.
        container.start();

        // Publish atomically; if another thread won the race for this key, tear ours down.
        ConcurrentMessageListenerContainer<?, ?> previous = listenerCache.putIfAbsent(key, container);
        if (previous != null) {
            shutdown(container);
            return;
        }

        // If the sandbox was stopped concurrently, do not leak a running container
        // (stop() snapshots the cache, so a container inserted afterwards would be orphaned).
        if (!started.get() && listenerCache.remove(key, container)) {
            shutdown(container);
            throw new IllegalStateException("KafkaSandbox was stopped during listener setup.");
        }

        logger.info("Listener started for topic {} group {}.", topicName, groupId);
    }

    public void seekConsumerGroupOffsets(String topicName, String groupId, Map<Integer, Long> partitionOffsets) {
        requireStarted();
        Objects.requireNonNull(topicName,        "TopicName must not be null.");
        Objects.requireNonNull(groupId,          "GroupId must not be null.");
        Objects.requireNonNull(partitionOffsets, "Partition offsets must not be null.");
        if (groupId.isEmpty() || topicName.isEmpty() || partitionOffsets.isEmpty())
            throw new IllegalArgumentException("Invalid parameters for consumer setup.");

        // Note: This method uses assign() (manual partition assignment) rather than subscribe().
        // The consumer will not trigger a group rebalance, so the committed offsets are written
        // directly to the group coordinator without joining the group. This is intentional for
        // administrative offset management, but the consumer group must already exist.
        TopicConfig cfg = getTopicConfig(topicName);
        try (KafkaConsumer<?, ?> consumer = new KafkaConsumer<>(buildConsumerConfig(groupId, cfg))) {
            List<TopicPartition> tps = partitionOffsets.keySet().stream()
                    .map(p -> new TopicPartition(topicName, p))
                    .toList();

            consumer.assign(tps);

            // Move the offsets to the desired position.
            for (TopicPartition tp : tps) {
                long offset = partitionOffsets.get(tp.partition());
                long end = consumer.endOffsets(List.of(tp)).get(tp);
                consumer.seek(tp, Math.min(offset, end));
            }

            consumer.poll(Duration.ofMillis(KafkaConstants.CST_POLL_DURATION));
            consumer.commitSync();
        } catch (Exception ex) {
            logger.warn("Failed to seek/commit offsets for group {} on topic {}: {}.", groupId, topicName, ex.getMessage());
            throw new IllegalStateException("Failed to seek/commit offsets for group " + groupId + " on topic " + topicName, ex);
        }
    }

    public KafkaConfig getCfg() {
        return cfg;
    }

    public String getBootstrapServers() {
        requireStarted();
        return brokerLifecycle.getBootstrapServers();
    }

    public Set<String> getTopics() {
        requireStarted();
        try {
            return brokerLifecycle.getTopics().stream()
                .filter(t -> KafkaConstants.CST_RESERVED_TOPIC_NAMES.stream().noneMatch(t::startsWith))
                .collect(Collectors.toUnmodifiableSet());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to retrieve topics from broker.", ex);
        }
    }

    public synchronized AdminClient getAdminClient() {
        requireStarted();

        if (kAdminClient != null)
            return kAdminClient;

        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,  brokerLifecycle.getBootstrapServers());
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, KafkaConstants.CST_REQUEST_TIMEOUT_MS_CONFIG);
        kAdminClient = AdminClient.create(props);

        return kAdminClient;
    }

    public Map<MetricName, ? extends Metric> getBrokerMetrics() {
        requireStarted();
        return brokerLifecycle.getBrokerMetrics();
    }

    public KafkaDashboard getDashboardData() throws Exception {
        AdminClient adm = getAdminClient();

        // 1. Cluster infos.
        DescribeClusterResult cluster = adm.describeCluster();
        String clusterId  = getCfg().uuidID().toString(); // cluster.clusterId().get();
        String clusterIp  = getBootstrapServers();
        int brokers       = cluster.nodes().get(KafkaConstants.CST_ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS).size();
        String controller = cluster.controller().get(KafkaConstants.CST_ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS).toString();

        // 2. List and details of Topics.
        Set<String> topicNames = adm.listTopics().names().get(KafkaConstants.CST_ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        DescribeTopicsResult topicDetails = adm.describeTopics(topicNames);
        Map<String, TopicDescription> descriptions =
            topicDetails.allTopicNames().get(KafkaConstants.CST_ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        List<TopicSummary> summaries = descriptions.values().stream()
            .map(d -> new TopicSummary(
                d.name(),
                d.partitions().size(),
                d.partitions().isEmpty() ? (short) 0 : (short) d.partitions().get(0).replicas().size(),
                "N/A" // Requires a describeConfigs call to be precise.
            ))
            .toList();

        // 3. Lags.
        List<GroupLagSummary> groupLagSummaries = calculateConsumerGroupsLag(adm);
        return new KafkaDashboard(clusterId, clusterIp, brokers, controller, summaries, groupLagSummaries);
    }

    public void flushDLTForTopic(String sourceTopic) throws Exception {

        requireStarted();
        TopicConfig cfg = getTopicConfig(sourceTopic);
        String dltName = cfg.dltName();

        if (dltName == null || dltName.isBlank())
            throw new IllegalStateException("No DLT configured for topic: " + sourceTopic);

        flushTopic(dltName);
    }

    public void flushTopic(String topic) throws Exception {
        AdminClient adminClient = getAdminClient();

        // Discover all partitions.
        var description = adminClient
            .describeTopics(java.util.List.of(topic))
            .allTopicNames()
            .get(KafkaConstants.CST_ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .get(topic);

        // Prepare the deletion request.
        Map<TopicPartition, RecordsToDelete> records = new HashMap<>();
        for (var partition : description.partitions()) {

            TopicPartition tp = new TopicPartition(topic, partition.partition());
            // Delete up to the partition high watermark.
            records.put(tp, RecordsToDelete.beforeOffset(-1L));
        }

        // Execute deletion and wait for completion.
        var result = adminClient.deleteRecords(records);
        result.all().get(KafkaConstants.CST_ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private List<GroupLagSummary> calculateConsumerGroupsLag(AdminClient adm) throws Exception {
        // 1. List all groups (universal API listGroups).
        Collection<GroupListing> allGroups =
            adm.listGroups().all().get(KafkaConstants.CST_ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        List<GroupLagSummary> summaries = new ArrayList<>();

        for (GroupListing group : allGroups) {
            // 2. Filter to keep only groups of type CONSUMER.
            boolean isConsumerGroup = group.type().map(type -> type == GroupType.CONSUMER).orElse(true);
            if (!isConsumerGroup)
                continue;

            String groupId = group.groupId();

            // 3. Retrieve the group's current offsets (where the consumer stands).
            Map<TopicPartition, OffsetAndMetadata> currentOffsets = adm
                .listConsumerGroupOffsets(groupId)
                .partitionsToOffsetAndMetadata()
                .get(KafkaConstants.CST_ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (currentOffsets.isEmpty()) {
                // STABLE, PREPARING_REBALANCE, COMPLETING_REBALANCE, DEAD, etc.
                // use describeConsumerGroups to get the real status.
                summaries.add(new GroupLagSummary(groupId, 0L, "UNKNOWN"));
                continue;
            }

            // 4. Identify the target partitions to request Log End Offsets (LEO).
            Set<TopicPartition> partitions = currentOffsets.keySet();

            // Prepare the query to obtain the most recent offset (Latest).
            Map<TopicPartition, OffsetSpec> latestSpecs = partitions.stream()
                .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));

            // 5. Retrieve the Log End Offsets (LEO) from the broker.
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffsets =
                adm.listOffsets(latestSpecs).all().get(KafkaConstants.CST_ADMIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // 6. Calculate the total lag (sum of gaps per partition).
            long totalLag = 0;
            for (TopicPartition tp : partitions) {
                long current = currentOffsets.get(tp).offset();

                // Safety in case the partition disappeared between calls.
                if (endOffsets.containsKey(tp)) {
                    long end = endOffsets.get(tp).offset();
                    // The lag cannot be negative (in theory).
                    totalLag += Math.max(0, end - current);
                }
            }

            // 7. Add to summary (could also include state via describeGroups if needed).
            summaries.add(new GroupLagSummary(groupId, totalLag, "ACTIVE"));
        }
        return summaries;
    }

    public DefaultErrorHandler getErrorHandler(String topicName) {
        requireStarted();
        Objects.requireNonNull(topicName, "Topic name must not be null.");
        final ErrorHandleCacheKey key = new ErrorHandleCacheKey(topicName);

        // Reuse existing handler.
        DefaultErrorHandler handler = errorHandlerCache.get(key);
        if (handler != null)
            return handler;

        // Retrieve source topic configuration.
        TopicConfig cfg = getTopicConfig(topicName);

        // A dedicated DLT must be configured.
        final String dltName = cfg.dltName().isBlank() ? 
            KafkaConstants.CST_DEFAULT_DLT : cfg.dltName();

        if (dltName.equals(KafkaConstants.CST_DEFAULT_DLT)) {
            // Make sure we have at least the default DLT topic.
            if (!getTopicRegistry().containsKey(KafkaConstants.CST_DEFAULT_DLT))
                addTopic(KafkaConstants.CST_DEFAULT_DLT);
        }

        if (topicName.equals(dltName))
            throw new IllegalStateException("Source topic and DLT must be different.");

        if (!getTopicRegistry().containsKey(dltName))
            throw new IllegalStateException("DLT is not registered: " + dltName);

        // Non-transactional producer for DLT recovery.
        KafkaTemplate<Object, Object> template =
            getKafkaTemplate(dltName, false);
        // To be clear ... Next release.
        // getKafkaTemplate(dltName, TRANSACTIONAL_ACK_MODES.contains(ackMode));

        // Use the retry policy of the source topic.
        handler = buildErrorHandler(template, cfg.retryInterval(), cfg.retryAttempts());

        // Publish atomically; if another thread won the race for this key, tear ours down.
        DefaultErrorHandler previous = errorHandlerCache.putIfAbsent(key, handler);
        if (previous != null)
            return previous;

        // If the sandbox was stopped concurrently, do not leak a running container
        // (stop() snapshots the cache, so a container inserted afterwards would be orphaned).
        if (!started.get() && errorHandlerCache.remove(key, handler))
            throw new IllegalStateException("KafkaSandbox was stopped during a template creation.");

        return handler;
    }

    public boolean isStarted() {
        return started.get();
    }

    public ConsumerGroupMonitor getConsumerGroupMonitor() {
        requireStarted();
        ConsumerGroupMonitor monitor = new ConsumerGroupMonitor(this);
        monitorRegistry.add(monitor);
        return monitor;
    }

    public void unregisterMonitor(ConsumerGroupMonitor monitor) {
        monitorRegistry.remove(monitor);
    }

    @Override
    public String toString() {
        String ports = started.get() ? brokerLifecycle.getBootstrapServers() : "<stopped>";
        return "KafkaSandbox[id=" + cfg.uuidID() +
               ", cluster=" + cfg.clusters() +
               ", clusterPartitions=" + cfg.clusterPartitions() +
               ", concurrency=" + cfg.concurrency() +
               ", autoCreateTopic=" + cfg.autoCreateTopic() +
               ", ports=" + ports + "]";
    }

    private <V, K> ProducerFactory<K, V> buildProducerFactory(TopicConfig cfg, Optional<String> trxID) {
        requireStarted();
        return new DefaultKafkaProducerFactory<>(buildProducerConfig(cfg, trxID));
    }

    private Map<String, Object> buildProducerConfig(TopicConfig tc, Optional<String> trxID) {
        Objects.requireNonNull(tc,                   "TopicConfig must not be null.");
        Objects.requireNonNull(tc.keySerializer(),   "TopicConfig.keySerializer() must not be null.");
        Objects.requireNonNull(tc.valueSerializer(), "TopicConfig.valueSerializer() must not be null.");
        Objects.requireNonNull(trxID,                "trxID must not be null (use Optional.empty()).");

        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,      brokerLifecycle.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   tc.keySerializer());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, tc.valueSerializer());
        props.put(ProducerConfig.ACKS_CONFIG,               KafkaConstants.CST_ACKS_CONFIG);
        props.put(ProducerConfig.RETRIES_CONFIG,            KafkaConstants.CST_RETRIES_CONFIG);
        props.put(ProducerConfig.LINGER_MS_CONFIG,          KafkaConstants.CST_LINGER_MS_CONFIG);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, KafkaConstants.CST_REQUEST_TIMEOUT_MS_CONFIG);
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG,       KafkaConstants.CST_MAX_BLOCK_MS_CONFIG);
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG,   KafkaConstants.CST_RETRY_BACKOFF_MS_CONFIG);

        trxID.ifPresent(id -> {
            props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG,   id);
            props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        });

        // Topic-specific overrides (if any).
        props.putAll(tc.extraProducerProps());
        return props;
    }

    private Map<String, Object> buildConsumerConfig(String groupId, TopicConfig tc) {
        Objects.requireNonNull(tc,                     "TopicConfig must not be null.");
        Objects.requireNonNull(tc.keyDeserializer(),   "TopicConfig.keyDeserializer() must not be null.");
        Objects.requireNonNull(tc.valueDeserializer(), "TopicConfig.valueDeserializer() must not be null.");

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerLifecycle.getBootstrapServers());
        
        if (groupId != null) // Optional for a direct consumer using assign.
            props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        else
            logger.warn("No group for ConsumerConfig. Only assign can be used !!!");
        
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   tc.keyDeserializer());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, tc.valueDeserializer());

        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG,    KafkaConstants.CST_ISOLATION_LEVEL_CONFIG);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,  KafkaConstants.CST_AUTO_OFFSET_RESET_CONFIG);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, KafkaConstants.CST_ENABLE_AUTO_COMMIT_CONFIG);

        // Topic-specific overrides (if any).
        props.putAll(tc.extraConsumerProps());
        return props;
    }

    private <K, V> DefaultErrorHandler buildErrorHandler(
            KafkaTemplate<K, V> template,
            long retryInterval,
            long retryAttempts) {

        Objects.requireNonNull(template, "KafkaTemplate must not be null.");
        if (retryInterval <= 0)
            throw new IllegalArgumentException("Retry interval must be > 0.");
        if (retryAttempts <= 0)
            throw new IllegalArgumentException("Retry attempts must be > 0.");

        // The template's default topic is the DLT.
        DeadLetterPublishingRecoverer recoverer =
            new DeadLetterPublishingRecoverer(
                template,
                (record, ex) -> new TopicPartition(
                    template.getDefaultTopic(),
                    KafkaConstants.CST_PARTITION_DLT
                )
            );

        // Preserve original record information.
        recoverer.setAppendOriginalHeaders(true);
        recoverer.setRetainExceptionHeader(true);

        // Verify DLT publication.
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setThrowIfNoDestinationReturned(true);

        // Retry policy.
        DefaultErrorHandler errorHandler =
            new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(
                    retryInterval,
                    retryAttempts
                )
            );

        // Commit recovered offsets with MANUAL_IMMEDIATE.
        errorHandler.setCommitRecovered(true);

        // Non-retryable exceptions.
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
        return errorHandler;
    }

    private void requireStarted() {
        if (!started.get())
            throw new IllegalStateException("KafkaSandbox must be started first.");
    }

    private String getUniqueTrxID() {
        return KafkaConstants.CST_TRX_PREFIX + UUID.randomUUID();
    }

    private void shutdown(ConsumerGroupMonitor cgm) {
        try { cgm.stopMonitoring(); } catch (Exception ex) {
            logger.warn("Exception stopping monitor: {}.", ex.getMessage());
        }
    }
    
    private <K, V> void shutdown(KafkaConsumer<K, V> consumer) {
        try { consumer.wakeup(); } catch (Exception ex) {
            logger.warn("Exception wakingup consumer: {}.", ex.getMessage());
        }
        try { consumer.close(); } catch (Exception ex) {
            logger.warn("Exception closing consumer: {}.", ex.getMessage());
        }
    }

    private <K,V> void shutdown(Producer<K, V> producer) {
        try { producer.flush(); } catch (Exception ex) {
            logger.warn("Exception flushing producer: {}.", ex.getMessage());
        }
        try { producer.close(Duration.ZERO); } catch (Exception ex) {
            logger.warn("Exception closing producer: {}.", ex.getMessage());
        }
    }
    
    private <K,V> void shutdown(KafkaTemplate<K,V> template) {
        try { template.flush(); } catch (Exception ex) {
            logger.warn("Exception flushing template: {}.", ex.getMessage());
        }
        try { template.destroy(); } catch (Exception ex) {
            logger.warn("Exception destroying template: {}.", ex.getMessage());
        }
    }
  
    private <K,V> void shutdown(ConcurrentMessageListenerContainer<K,V> listener) {
        try { listener.stop(); } catch (Exception ex) {
            logger.warn("Exception stopping listener container: {}.", ex.getMessage());
        }

        if (listener.isRunning()) // Let's the listerner drain messages.
            try { Thread.sleep(KafkaConstants.CST_POLL_DURATION); } catch (InterruptedException ex) {
                logger.warn("Exception waiting listener container: {}.", ex.getMessage());
            }

            try { listener.destroy(); } catch (Exception ex) {
            logger.warn("Exception destroying listener container: {}.", ex.getMessage());
        }
    }

    @Override
    public void preProcess(TopicConfig topicConfig) {
        requireStarted();
    }

    @Override
    public void postProcess(TopicConfig topicConfig) {
        brokerLifecycle.syncTopic(topicConfig);
    }
}
