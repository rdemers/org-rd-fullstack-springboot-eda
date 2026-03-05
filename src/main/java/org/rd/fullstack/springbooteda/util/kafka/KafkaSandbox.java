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
 * EmbeddedKafka
 * -------------
 * EmbeddedKafka is part of the Spring Kafka testing library and provides 
 * an in-memory, lightweight Kafka broker. It’s an excellent choice for unit 
 * and integration tests because it allows you to run Kafka within the JVM 
 * process of your test, avoiding the need for external Kafka infrastructure.
 */
package org.rd.fullstack.springbooteda.util.kafka;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.GroupListing;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
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
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.GenericMessageListener;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.listener.AcknowledgingMessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import org.springframework.util.backoff.FixedBackOff;

import jakarta.annotation.PreDestroy;
import kafka.server.BrokerServer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class KafkaSandbox extends AbstractTopicHandler<KafkaSandbox> {
    private static final Logger logger =
            LoggerFactory.getLogger(KafkaSandbox.class);

    private EmbeddedKafkaKraftBroker     kBroker;
    private volatile AdminClient         kAdminClient;
    private volatile DefaultErrorHandler kErrorHandler;

    private final KafkaConfig cfg;
    private final Map<ProducerCacheKey, Producer<?, ?>> producerCache;
    private final Map<TemplateCacheKey, KafkaTemplate<?, ?>> templateCache;
    private final Map<ListenerCacheKey, ConcurrentMessageListenerContainer<?, ?>> listenerCache;
    private final Set<ConsumerGroupMonitor> monitorRegistry;

    private record ProducerCacheKey(String topicName, String suffixe) {}
    private record TemplateCacheKey(String topicName, String suffixe) {}
    private record ListenerCacheKey(int concurrency, String groupId, String topicName) {}

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractTopicHandler<Builder> {
        private int clusters          = KafkaConstant.CST_NBR_CLUSTERS;
        private int clusterPartitions = KafkaConstant.CST_NBR_CLUSTERS_PARTITIONS;
        private int concurrency       = KafkaConstant.CST_NBR_CONCURRENCY;

        private boolean autoCreateTopic = false;
        private boolean autoStart       = false;

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

        public KafkaSandbox build() {
            KafkaConfig cfg = new KafkaConfig(
                UUID.randomUUID(), clusters, clusterPartitions, concurrency, autoCreateTopic
            );

            KafkaSandbox kafkaSandbox = new KafkaSandbox(cfg, getTopicRegistry());
            if (autoStart) 
                kafkaSandbox.start();

            return kafkaSandbox;
        }

        @Override
        public void preProcess(TopicConfig topicConfig) { /* Nothing to do in the builder. */ }

        @Override
        public void postProcess(TopicConfig topicConfig) { /* Nothing to do in the builder. */ }
    }

    private KafkaSandbox(KafkaConfig cfg, Map<String, TopicConfig> topicRegistry) {
        super(topicRegistry);
        this.cfg = Objects.requireNonNull(cfg, "KafkaConfig must not be null.");

        if (cfg.clusters() <= 0 || cfg.clusterPartitions() <= 0 || cfg.concurrency() <= 0)
            throw new IllegalArgumentException("Invalid configuration.");

        this.kBroker = null;
        this.kAdminClient = null;
        this.kErrorHandler = null;

        this.producerCache   = new ConcurrentHashMap<>();
        this.templateCache   = new ConcurrentHashMap<>();
        this.listenerCache   = new ConcurrentHashMap<>();
        this.monitorRegistry = ConcurrentHashMap.newKeySet(); 
    }

    public synchronized void start() {
        if (kBroker != null)
            return;

        EmbeddedKafkaKraftBroker broker = new EmbeddedKafkaKraftBroker(cfg.clusters(), cfg.clusterPartitions());
        broker.brokerProperties(buildBrokerProperties(cfg));
        broker.afterPropertiesSet();
        kBroker = broker;

        Map<String, TopicConfig> topicRegistry = getTopicRegistry();
        if (!topicRegistry.isEmpty()) {
            topicRegistry.values().forEach(this::synchTopicBroker);
            logger.info("Topics created: {}.", topicRegistry.keySet());
        }  

        logger.info("KafkaSandbox started on: {}.", kBroker.getBrokersAsString());
    }

    @PreDestroy
    public synchronized void stop() {
        if (kBroker == null)
            return;

        monitorRegistry.forEach(ConsumerGroupMonitor::stopMonitoring);
        monitorRegistry.clear();

        listenerCache.values().forEach(lc -> {
            lc.stop();
            lc.destroy();
        });
        listenerCache.clear();

        templateCache.values().forEach(tc -> {
            tc.flush();
            tc.destroy();
        });
        templateCache.clear();

        producerCache.values().forEach(pc -> {
            pc.flush();
            pc.close();
        });
        producerCache.clear();

        if (kAdminClient != null) {
            try {
                kAdminClient.close(Duration.ofSeconds(KafkaConstant.CST_ADMIN_CLOSE_TIMEOUT_SECONDS));
            } catch (Exception ex) {
                logger.warn("Exception closing AdminClient: {}.", ex);
            }
        }
 
        try {
            kBroker.destroy();
        } catch (Exception ex) {
            logger.warn("Exception destroying Kafka Broker: {}.", ex);
        }
        
        kBroker = null;
        kAdminClient = null;
        kErrorHandler = null;
        logger.info("KafkaSandbox stopped and all resources released.");
    }

    public <K, V> Producer<K, V> getProducer(String topicName) {
        return getProducer(topicName, false);
    }

    @SuppressWarnings("unchecked")
    public synchronized <K, V> Producer<K, V> getProducer(String topicName, boolean indTrxFlag) {
        requireStarted();
        Objects.requireNonNull(topicName, "topicName must not be null.");

        final ProducerCacheKey key = 
            new ProducerCacheKey(topicName,
                (indTrxFlag ? KafkaConstant.CST_TRX_TRX_POSTFIX : KafkaConstant.CST_TRX_NO_TRX_POSTFIX));

        return (Producer<K, V>) producerCache.computeIfAbsent(key, t -> {
            final TopicConfig cfg = getTopicConfig(topicName);

            Producer<K, V> producer = 
                new KafkaProducer<>(buildProducerConfig(cfg, 
                    (indTrxFlag ? Optional.of(getUniqueTrxID()) : Optional.empty())));

            if (indTrxFlag)
                producer.initTransactions();

            return producer;
        });
    }

    public <K, V> KafkaTemplate<K, V> getKafkaTemplate(String topicName) {
        return getKafkaTemplate(topicName, false);
    }

    @SuppressWarnings("unchecked")
    public synchronized <K, V> KafkaTemplate<K, V> getKafkaTemplate(String topicName, boolean indTrxFlag) {
        requireStarted();
        Objects.requireNonNull(topicName, "topicName must not be null.");

        final TemplateCacheKey key = 
            new TemplateCacheKey(topicName,
                (indTrxFlag ? KafkaConstant.CST_TRX_TRX_POSTFIX : KafkaConstant.CST_TRX_NO_TRX_POSTFIX));
 
        return (KafkaTemplate<K, V>) templateCache.computeIfAbsent(key, t -> {
            final TopicConfig cfg  = getTopicConfig(topicName);

            ProducerFactory<K, V> pf = buildProducerFactory(cfg, 
                (indTrxFlag ? Optional.of(getUniqueTrxID()) : Optional.empty()));
            
            KafkaTemplate<K, V> template = new KafkaTemplate<>(pf);
            template.setDefaultTopic(topicName);
            return template;
        });
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

    public synchronized <K, V> void setupMessageListener(int concurrency, String groupId, String topicName, 
                                                         AckMode ackMode, GenericMessageListener<ConsumerRecord<K, V>> listener) {
        requireStarted();
        Objects.requireNonNull(topicName, "topicName must not be null.");
        Objects.requireNonNull(groupId, "groupId must not be null.");
        Objects.requireNonNull(ackMode, "ackMode must not be null.");
        Objects.requireNonNull(listener, "Listener must not be null.");
        if (groupId.isEmpty() || topicName.isEmpty() || concurrency <= 0)
            throw new IllegalArgumentException("Invalid parameters for listener setup.");

        final ListenerCacheKey key = new ListenerCacheKey(concurrency, groupId, topicName);   
        listenerCache.computeIfAbsent(key, k -> {
            TopicConfig cfg = getTopicConfig(topicName);
            if (concurrency > cfg.partitions())
                logger.warn("The concurrent threads requested is greater than the partitions (currency: {}, partitions: {}).",
                             concurrency, cfg.partitions());

            ContainerProperties props = new ContainerProperties(topicName);
            props.setAckMode(ackMode);

            ConcurrentMessageListenerContainer<K, V> container =
                new ConcurrentMessageListenerContainer<>(
                    new DefaultKafkaConsumerFactory<>(buildConsumerConfig(groupId, cfg)), props);

            // --- 1. KafkaTemplate pour la DLT ---
            final String dltTarget = cfg.dltName().isBlank() ? KafkaConstant.CST_DEFAULT_DLT : cfg.dltName();
            KafkaTemplate<K, V> dltTemplate = getKafkaTemplate(dltTarget);

            // --- 2. DeadLetterPublishingRecoverer ---
            DefaultErrorHandler errorHandler = buildErrorHandler(dltTemplate, cfg.retryInterval(), cfg.retryAttempts());            
            container.setCommonErrorHandler(errorHandler);
            container.setupMessageListener(listener);
            container.setConcurrency(concurrency);
            container.start();

            logger.info("Listener started for topic {} group {}.", topicName, groupId);
            return container;
        });
    }

    public void seekConsumerGroupOffsets(String topicName, String groupId, Map<Integer, Long> partitionOffsets) {
        requireStarted();
        Objects.requireNonNull(topicName, "TopicName must not be null.");
        Objects.requireNonNull(groupId, "GroupId must not be null.");
        Objects.requireNonNull(partitionOffsets, "Partition offsets must not be null.");
        if (groupId.isEmpty() || topicName.isEmpty() || partitionOffsets.isEmpty()) 
            throw new IllegalArgumentException("Invalid parameters for consumer setup.");

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

            try {
                consumer.poll(Duration.ofMillis(KafkaConstant.CST_POOL_DURATION));
                consumer.commitSync();
            } catch (Exception e) {
                logger.warn("Failed to commit offsets for group {} - {}.", groupId, e);
            }
        }
    }

    public KafkaConfig getCfg() {
        return cfg;
    }
    
    public String getBootstrapServers() {
        requireStarted();
        return kBroker.getBrokersAsString();
    }

    public Set<String> getTopics() {
        requireStarted();
        return kBroker.getTopics();
    }  

    public synchronized AdminClient getAdminClient() {
        requireStarted();

        if (kAdminClient != null)
            return kAdminClient;

        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, getBootstrapServers());
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, KafkaConstant.CST_REQUEST_TIMEOUT_MS_CONFIG);
        kAdminClient = AdminClient.create(props);

        return kAdminClient;
    }

    public Map<MetricName, ? extends Metric> getBrokerMetrics() {
        requireStarted();
        Map<Integer, BrokerServer> brokers = kBroker.getCluster().brokers();
        if (brokers.isEmpty())
            return Collections.emptyMap();

        return brokers.get(0).metrics().metrics();
    }

    public KafkaDashboard getDashboardData() throws Exception {
        requireStarted();
        AdminClient adm = getAdminClient();

        // 1. Cluster Infos.
        DescribeClusterResult cluster = adm.describeCluster();
        String clusterId  = getCfg().uuidID().toString(); //cluster.clusterId().get();
        String clusterIp  = getBootstrapServers(); 
        int brokers       = cluster.nodes().get().size();
        String controller = cluster.controller().get().toString();

        // 2. List and details of Topics.
        Set<String> topicNames = adm.listTopics().names().get();
        DescribeTopicsResult topicDetails = adm.describeTopics(topicNames);
        Map<String, TopicDescription> descriptions = topicDetails.allTopicNames().get();

        List<TopicSummary> summaries = descriptions.values().stream()
            .map(d -> new TopicSummary(
                d.name(),
                d.partitions().size(),
                (short) d.partitions().get(0).replicas().size(),
                "N/A" // Requires a describeConfigs call to be precise.
            ))
            .toList();

        // 3. Lags.
        List<GroupLagSummary> groupLagSummaries = calculateConsumerGroupsLag(adm);
        return new KafkaDashboard(clusterId, clusterIp, brokers, controller, summaries, groupLagSummaries);
    }

    private List<GroupLagSummary> calculateConsumerGroupsLag(AdminClient adm) throws Exception {
        // 1. List all groups (universal API listGroups).
        Collection<GroupListing> allGroups = adm.listGroups().all().get();
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
                .get();

            if (currentOffsets.isEmpty()) {
                // STABLE, PREPARING_REBALANCE, COMPLETING_REBALANCE, DEAD, etc. 
                // use describeConsumerGroups ... To get the real status.
                summaries.add(new GroupLagSummary(groupId, 0L, "UNKNOWN"));
                continue;
            }

            // 4. Identify the target partitions to request Log End Offsets (LEO).
            Set<TopicPartition> partitions = currentOffsets.keySet();
            
            // We prepare the query to obtain the most recent offset (Latest).
            Map<TopicPartition, OffsetSpec> latestSpecs = partitions.stream()
                .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));

            // 5. Retrieve the Log End Offsets (LEO) from the broker
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> endOffsets =
                adm.listOffsets(latestSpecs).all().get();

            // 6. Calculate the total lag (Sum of the gaps per partition).
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

            // 7. Add to summary (We could also include the state via describeGroups if needed).
            summaries.add(new GroupLagSummary(groupId, totalLag, "ACTIVE"));
        }
        return summaries;
    }
 
    public synchronized DefaultErrorHandler getDefaultErrorHandler() {
        requireStarted();

        if (kErrorHandler != null)
            return kErrorHandler;

        try {
            getTopicConfig(KafkaConstant.CST_DEFAULT_DLT);
        } catch (Exception ex) {
            addTopic(KafkaConstant.CST_DEFAULT_DLT); // Add a topic ... DLT.
        }

        KafkaTemplate<Object, Object> template = 
            getKafkaTemplate(KafkaConstant.CST_DEFAULT_DLT);
        kErrorHandler = buildErrorHandler(template);

        return kErrorHandler;
    }

    public boolean isStarted() { 
        return kBroker != null; 
    }

    public ConsumerGroupMonitor getConsumerGroupMonitor() {
        requireStarted();
        ConsumerGroupMonitor monitor = new ConsumerGroupMonitor(this);
        monitorRegistry.add(monitor);
        return monitor;
    }

    void unregisterMonitor(ConsumerGroupMonitor monitor) {
        monitorRegistry.remove(monitor);
    }

    @Override
    public String toString() {
        String ports = (kBroker == null) ? "<stopped>" : kBroker.getBrokersAsString();
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

    private Map<String, String> buildBrokerProperties(KafkaConfig cfg) {
        return Map.of(
                //  Not needed, auto-generated by EmbeddedKafkaKraftBroker.
                //  "cluster.id", cfg.uuidID().toString().
                "auto.create.topics.enable", String.valueOf(cfg.autoCreateTopic()),
                "log.retention.hours", "1",
                "transaction.state.log.replication.factor", "1",
                "transaction.state.log.min.isr", "1",
                "offsets.topic.replication.factor", "1"
        );
    }

    private Map<String, Object> buildProducerConfig(TopicConfig tc, Optional<String> trxID) {
        Objects.requireNonNull(tc,                   "TopicConfig must not be null.");
        Objects.requireNonNull(tc.keySerializer(),   "TopicConfig.keySerializer() must not be null.");
        Objects.requireNonNull(tc.valueSerializer(), "TopicConfig.valueSerializer() must not be null.");
        Objects.requireNonNull(trxID,                "trxID must not be null (use Optional.empty()).");

        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,      kBroker.getBrokersAsString());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   tc.keySerializer());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, tc.valueSerializer());
        props.put(ProducerConfig.ACKS_CONFIG,                 KafkaConstant.CST_ACKS_CONFIG);
        props.put(ProducerConfig.RETRIES_CONFIG,              KafkaConstant.CST_RETRIES_CONFIG);
        props.put(ProducerConfig.LINGER_MS_CONFIG,            KafkaConstant.CST_LINGER_MS_CONFIG);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,   KafkaConstant.CST_REQUEST_TIMEOUT_MS_CONFIG);
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG,         KafkaConstant.CST_MAX_BLOCK_MS_CONFIG);
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG,     KafkaConstant.CST_RETRY_BACKOFF_MS_CONFIG);

        trxID.ifPresent(id -> {
            props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG,  id);
            props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        });

        // Topic-specific overrides (if any).
        props.putAll(tc.extraProducerProps());
        return props;
    }

    private Map<String, Object> buildConsumerConfig(String groupId, TopicConfig tc) {
        Objects.requireNonNull(tc, "TopicConfig must not be null.");
        Objects.requireNonNull(tc.keyDeserializer(), "TopicConfig.keyDeserializer() must not be null.");
        Objects.requireNonNull(tc.valueDeserializer(), "TopicConfig.valueDeserializer() must not be null.");

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kBroker.getBrokersAsString());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, tc.keyDeserializer());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, tc.valueDeserializer());
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, KafkaConstant.CST_ISOLATION_LEVEL_CONFIG);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, KafkaConstant.CST_AUTO_OFFSET_RESET_CONFIG);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, KafkaConstant.CST_ENABLE_AUTO_COMMIT_CONFIG);

        // Topic-specific overrides (if any).
        props.putAll(tc.extraConsumerProps());
        return props;
    }
    
    private <K, V> DefaultErrorHandler buildErrorHandler(KafkaTemplate<K, V> template) {
        return buildErrorHandler(template, KafkaConstant.CST_RETRY_INTERVAL, 
                                           KafkaConstant.CST_RETRY_ATTEMPTS);
    }

    private <K, V> DefaultErrorHandler buildErrorHandler(KafkaTemplate<K,V> template, 
                                                         long retryInterval, long retryAttempts) {
        Objects.requireNonNull(template, "KafkaTemplate must not be null.");
        if (retryInterval <= 0)
            throw new IllegalArgumentException("Retry interval must be > 0.");

        if (retryAttempts <= 0)
            throw new IllegalArgumentException("Retry attempts must be > 0.");

        DeadLetterPublishingRecoverer recoverer =
            new DeadLetterPublishingRecoverer(template,
            (record, ex) -> new TopicPartition(template.getDefaultTopic(), KafkaConstant.CST_PARTITION_DLT)
        );

        // Our options.
        recoverer.setAppendOriginalHeaders(true);
        recoverer.setRetainExceptionHeader(true);
        recoverer.setThrowIfNoDestinationReturned(false);

         // Using default values as TopicConfig is not available in this scope.
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, 
            new FixedBackOff(retryInterval, retryAttempts));

        // Non-retryable exceptions (optional but recommended).
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
        return errorHandler;
    }

    private void requireStarted() {
        if (!isStarted()) 
            throw new IllegalStateException("KafkaSandbox must be started first.");
    }

    private String getUniqueTrxID() {
        return KafkaConstant.CST_TRX_PREFIX + UUID.randomUUID();
    }

    private void synchTopicBroker(TopicConfig topicConfig) {

        // Possibly a duplicate. 
        // DLT topical entries cause a form of duplication.
        // Logic to be improved in a future release.
        if (kBroker.getTopics().contains(topicConfig.name()))
            return;

        kBroker.addTopics(new NewTopic(topicConfig.name(),
                          topicConfig.partitions(), topicConfig.replicas()));

        // Specific DLT - Dead Letter Topic ?
        if (!topicConfig.dltName().isEmpty()) {
            kBroker.addTopics(new NewTopic(topicConfig.dltName(),
                topicConfig.partitions(), topicConfig.replicas()));
        }
    }

    @Override
    public void preProcess(TopicConfig topicConfig) {
        requireStarted();
    }

    @Override
    public void postProcess(TopicConfig topicConfig) {
        synchTopicBroker(topicConfig);
    }
}