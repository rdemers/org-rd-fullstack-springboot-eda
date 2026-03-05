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
import java.util.Objects;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;

/**
 * Immutable description of a Kafka topic and its serialization configuration.
 * <p>
 * This record is intentionally NON-generic.
 * Kafka does not use Java generics at runtime; typing is enforced at the
 * producer/listener boundaries, not at the topic definition level.
 */
public record TopicConfig (
        String name,        // Main topic name, required.
        String dltName,     // Dead Letter Topic name, optional.

        int partitions,     // Number of partitions for the topic.
        short replicas,     // Number of replicas for the topic.        
        long retryInterval, // Milliseconds to wait before retrying a failed message.
        long retryAttempts, // Number of times to retry sending a message.

        Class<? extends Serializer<?>> keySerializer,
        Class<? extends Serializer<?>> valueSerializer,

        Class<? extends Deserializer<?>> keyDeserializer,
        Class<? extends Deserializer<?>> valueDeserializer,

        Map<String, Object> extraProducerProps,
        Map<String, Object> extraConsumerProps) {

    public TopicConfig {
        Objects.requireNonNull(name, "Topic name must not be null.");
        if (name.isBlank())
            throw new IllegalArgumentException("Topic name must not be blank.");

        if (isReservedTopicName(name))
            throw new IllegalArgumentException("Topic name is reserved: " + name);

        if (!name.matches("[a-zA-Z0-9._-]+"))
            throw new IllegalArgumentException("Invalid Kafka topic name.");

        if (!dltName.isBlank()) { // If DLT name is provided.
            if (isReservedTopicName(dltName))
                throw new IllegalArgumentException("Topic name is reserved: " + dltName);

            if (!dltName.matches("[a-zA-Z0-9._-]+"))
                throw new IllegalArgumentException("Invalid Kafka DLT name.");

            if (retryInterval <= 0)
                throw new IllegalArgumentException("Retry interval must be > 0.");

            if (retryAttempts <= 0)
                throw new IllegalArgumentException("Retry attempts must be > 0.");
        }

        if (partitions <= 0)
            throw new IllegalArgumentException("Partitions must be > 0.");

        if (replicas <= 0)
            throw new IllegalArgumentException("Replicas must be > 0.");

        Objects.requireNonNull(keySerializer, "Key serializer must not be null.");
        Objects.requireNonNull(valueSerializer, "Value serializer must not be null.");
        Objects.requireNonNull(keyDeserializer, "Key deserializer must not be null.");
        Objects.requireNonNull(valueDeserializer, "Value deserializer must not be null.");

        extraProducerProps =
            extraProducerProps == null ? Map.of() : Map.copyOf(extraProducerProps);
        extraConsumerProps =
            extraConsumerProps == null ? Map.of() : Map.copyOf(extraConsumerProps);
    }

    public TopicConfig(String name, String dltName, int partitions, short replicas,
            Class<? extends Serializer<?>> keySerializer,
            Class<? extends Serializer<?>> valueSerializer,
            Class<? extends Deserializer<?>> keyDeserializer,
            Class<? extends Deserializer<?>> valueDeserializer) {

        this(name, dltName, partitions, replicas, KafkaConstant.CST_RETRY_INTERVAL, 
                                                  KafkaConstant.CST_RETRY_ATTEMPTS, keySerializer, valueSerializer, 
             keyDeserializer, valueDeserializer, Map.of(), Map.of());
    }

    public boolean hasExtraProducerProps() {
        return !extraProducerProps.isEmpty();
    }

    public boolean hasExtraConsumerProps() {
        return !extraConsumerProps.isEmpty();
    }

    public static boolean isReservedTopicName(String topicName) {
        return KafkaConstant.CST_RESERVED_TOPIC_NAMES.stream()
            .anyMatch(reserved -> topicName.startsWith(reserved));
    }
}