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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigProvider {
    private static final Logger logger = LoggerFactory.getLogger(ConfigProvider.class);

    private ConfigProvider() {} // No instanciation.

    public static Map<String, Object> buildProducerConfig(String bootstrapServers) {
        return buildProducerConfig(bootstrapServers, null, Optional.empty());
    }

    public static Map<String, Object> buildProducerConfig(String bootstrapServers, TopicConfig tc) {
        return buildProducerConfig(bootstrapServers, tc, Optional.empty());
    }

    public static Map<String, Object> buildProducerConfig(String bootstrapServers, TopicConfig tc, Optional<String> trxID) {
        
        Objects.requireNonNull(bootstrapServers, "bootstrapServers must not be null.");
        Objects.requireNonNull(trxID, "trxID must not be null (use Optional.empty()).");

        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,   
            (tc != null) ? tc.keySerializer() : StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, 
            (tc != null) ? tc.valueSerializer() : StringSerializer.class);

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
        if (tc != null)
            props.putAll(tc.extraProducerProps());
        return props;
    }

    public static Map<String, Object> buildConsumerConfig(String bootstrapServers) {
        return buildConsumerConfig(bootstrapServers, null, null);
    }

    public static Map<String, Object> buildConsumerConfig(String bootstrapServers, String groupId) {
        return buildConsumerConfig(bootstrapServers, groupId, null);
    }

    public static Map<String, Object> buildConsumerConfig(String bootstrapServers, String groupId, TopicConfig tc) {

        Objects.requireNonNull(bootstrapServers, "bootstrapServers must not be null.");

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        if (groupId != null)
            props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        else
            logger.warn("No group for ConsumerConfig !!!");
        
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   
            (tc != null) ? tc.keyDeserializer() : StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, 
            (tc != null) ? tc.valueDeserializer() : StringDeserializer.class);

        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG,    KafkaConstants.CST_ISOLATION_LEVEL_CONFIG);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,  KafkaConstants.CST_AUTO_OFFSET_RESET_CONFIG);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, KafkaConstants.CST_ENABLE_AUTO_COMMIT_CONFIG);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,   KafkaConstants.CST_MAX_POLL_RECORDS);

        // Activation of the KIP-848 rebalancing protocol (replaces "classic")
        props.put(ConsumerConfig.GROUP_PROTOCOL_CONFIG, "consumer");

        // Topic-specific overrides (if any).
        if (tc != null)
            props.putAll(tc.extraConsumerProps());

        return props;
    }

    public static Map<String, Object> buildShareConsumerConfig(String bootstrapServers) {
        return buildShareConsumerConfig(bootstrapServers, null, null);
    }

    public static Map<String, Object> buildShareConsumerConfig(String bootstrapServers, String groupId) {
        return buildShareConsumerConfig(bootstrapServers, groupId, null);
    }
    
    public static Map<String, Object> buildShareConsumerConfig(String bootstrapServers, String groupId, TopicConfig tc) {

        Objects.requireNonNull(bootstrapServers, "bootstrapServers must not be null.");

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        if (groupId != null && !groupId.isBlank())
            props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        else
            logger.warn("No group for ShareConsumerConfig !!!");

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            (tc != null) ? tc.keyDeserializer() : StringDeserializer.class);

        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            (tc != null) ? tc.valueDeserializer() : StringDeserializer.class);

        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, KafkaConstants.CST_MAX_POLL_RECORDS);
        props.put(ConsumerConfig.SHARE_ACKNOWLEDGEMENT_MODE_CONFIG, "explicit");

        // Enforce max.poll.records as a strict limit.
        props.put(ConsumerConfig.SHARE_ACQUIRE_MODE_CONFIG,"record_limit");

        // Topic-specific overrides (if any)
        if (tc != null)
            props.putAll(tc.extraConsumerProps());

        return props;
    }
}