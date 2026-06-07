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

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

abstract class AbstractTopicHandler<T extends AbstractTopicHandler<T>> implements TopicHandler<T> {

    private final Map<String, TopicConfig> topicRegistry;

    public AbstractTopicHandler() {
        this.topicRegistry = new ConcurrentHashMap<>();
    }

    public AbstractTopicHandler(Map<String, TopicConfig> topicRegistry) {
        Objects.requireNonNull(topicRegistry, "TopicRegistry must not be null.");
        this.topicRegistry = new ConcurrentHashMap<>(topicRegistry);
    }

    public T addTopic(String name) {
        return addTopic(name, KafkaConstants.CST_NO_DLT, 
                KafkaConstants.CST_NBR_TOPICS_PARTITIONS, KafkaConstants.CST_NBR_TOPICS_REPLICAS,
                KafkaConstants.CST_RETRY_ATTEMPTS, KafkaConstants.CST_RETRY_INTERVAL, 
                StringSerializer.class, StringSerializer.class,
                StringDeserializer.class, StringDeserializer.class);
    }

    public T addTopic(String name, String dltName) {
        return addTopic(name, dltName,
                KafkaConstants.CST_NBR_TOPICS_PARTITIONS, KafkaConstants.CST_NBR_TOPICS_REPLICAS,
                KafkaConstants.CST_RETRY_ATTEMPTS, KafkaConstants.CST_RETRY_INTERVAL,
                StringSerializer.class, StringSerializer.class,
                StringDeserializer.class, StringDeserializer.class);
    }

    public T  addTopic(String name, int partitions, short replicas) {
        return addTopic(name, KafkaConstants.CST_NO_DLT, 
                partitions, replicas, 
                KafkaConstants.CST_RETRY_ATTEMPTS, KafkaConstants.CST_RETRY_INTERVAL, 
                StringSerializer.class, StringSerializer.class,
                StringDeserializer.class, StringDeserializer.class);
    }

    public T addTopic(String name, String dltName, int partitions, short replicas) {
        return addTopic(name, dltName,
                partitions, replicas,
                KafkaConstants.CST_RETRY_ATTEMPTS, KafkaConstants.CST_RETRY_INTERVAL,
                StringSerializer.class, StringSerializer.class,
                StringDeserializer.class, StringDeserializer.class);
    }

    public T addTopic(String name, String dltName, int partitions, short replicas,
                      long retryAttempts, long retryInterval) {
        return addTopic(name, dltName, partitions, replicas, retryAttempts, retryInterval,
                StringSerializer.class, StringSerializer.class,
                StringDeserializer.class, StringDeserializer.class);
    }

    public T addTopic(String name, String dltName, int partitions, short replicas, 
                long retryAttempts, long retryInterval, 
                Class<? extends Serializer<?>> keySerializer, 
                Class<? extends Serializer<?>> valueSerializer,
                Class<? extends Deserializer<?>> keyDeserializer, 
                Class<? extends Deserializer<?>> valueDeserializer) {
        return addTopic(name, dltName, 
                partitions, replicas, retryAttempts, retryInterval, 
                keySerializer, valueSerializer, keyDeserializer, 
                valueDeserializer, Map.of(), Map.of());
    }

    public T addTopic(String name, String dltName, int partitions, short replicas, 
                long retryAttempts, long retryInterval, 
                Class<? extends Serializer<?>> keySerializer, 
                Class<? extends Serializer<?>> valueSerializer,
                Class<? extends Deserializer<?>> keyDeserializer, 
                Class<? extends Deserializer<?>> valueDeserializer,
                Map<String, Object> extraProducerProps, Map<String, Object> extraConsumerProps) {
        TopicConfig topicConfig = new TopicConfig(name, dltName, 
                partitions, replicas, retryAttempts, retryInterval, 
                keySerializer, valueSerializer, keyDeserializer, valueDeserializer, 
                extraProducerProps, extraConsumerProps);

        return addTopics(topicConfig);
    }

    @SuppressWarnings("unchecked")
    public T addTopics(TopicConfig... topics) {
        Objects.requireNonNull(topics, "TopicConfig must not be null.");
        Arrays.stream(topics).forEach(topicConfig -> {
            preProcess(topicConfig);
            TopicConfig existing;

            existing = topicRegistry.putIfAbsent(topicConfig.name(), topicConfig);
            if (existing != null)
                throw new IllegalArgumentException(
                    "The topic already exists: " + topicConfig.name()
                );

            if (!topicConfig.dltName().isEmpty()) {
                TopicConfig dltConfig = buildDltTopicConfig(topicConfig);
                existing = topicRegistry.putIfAbsent(topicConfig.dltName(), dltConfig);
                if (existing != null)
                    throw new IllegalArgumentException(
                        "The topic already exists: " + topicConfig.dltName()
                    );
            }
            try {
                postProcess(topicConfig);
            } catch (Exception ex) {
                // Rollback.
                topicRegistry.remove(topicConfig.name());
                if (!topicConfig.dltName().isEmpty())
                    topicRegistry.remove(topicConfig.dltName());

                throw ex;
            }
        });

        return (T)this;
    }

    public TopicConfig getTopicConfig(String name) {
        TopicConfig cfg = topicRegistry.get(name);
        if (cfg == null)
            throw new IllegalArgumentException("Topic not registered: " + name);
        return cfg;
    }

    public Map<String, TopicConfig> getTopicRegistry() {
        return Collections.unmodifiableMap(this.topicRegistry);
    }

    private TopicConfig buildDltTopicConfig(TopicConfig parent) {
        return new TopicConfig(
            parent.dltName(),
            KafkaConstants.CST_NO_DLT,
            parent.partitions(),
            parent.replicas(),
            parent.retryAttempts(),
            parent.retryInterval(),
            parent.keySerializer(),
            parent.valueSerializer(),
            parent.keyDeserializer(),
            parent.valueDeserializer(),
            parent.extraProducerProps(),
            parent.extraConsumerProps()
        );
    }

    public abstract void preProcess(TopicConfig topicConfig);
    public abstract void postProcess(TopicConfig topicConfig);
}