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
package org.rd.fullstack.springbooteda.config;

import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.rd.fullstack.springbooteda.util.kafka.KafkaSandbox;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.DefaultErrorHandler;

@Configuration
public class KafkaConfig {
    @Value("${org.rd.fullstack.springbooteda.kafka.sandbox.enabled}")
    private boolean kEnabled;

    @Value("${org.rd.fullstack.springbooteda.kafka.sandbox.clusters}")
    private int clusters;

    @Value("${org.rd.fullstack.springbooteda.kafka.sandbox.cluster-partitions}")
    private short clusterPartitions;

    @Value("${org.rd.fullstack.springbooteda.kafka.sandbox.concurrency}")
    private int concurrency;

    @Bean
    @SuppressWarnings("deprecation")
    KafkaSandbox kafkaSandbox() {
        return KafkaSandbox.builder()
            .clusters(clusters)
            .clusterPartitions(clusterPartitions)
            .addTopic("APP-Kafka-Request", "APP-Kafka-Request-dlt", 8, (short) 1)
            .addTopic("APP-Flink-Request", "APP-Flink-Request-dlt", 8, (short) 1)
            .autoStart(kEnabled)
            .build();
    }

    @Bean
    ProducerFactory<String, String> producerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfigs());
    }

    @Bean
    KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // A KafkaListenerContainerFactory is required for the @KafkaListener annotation to work.
    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(kafkaSandbox().getCfg().concurrency());
        factory.getContainerProperties().setAckMode(AckMode.MANUAL_IMMEDIATE);
        factory.setCommonErrorHandler(errorHandler());
        return factory;
    }

    @Bean
    ConsumerFactory<String, String> consumerFactory() {
        //JsonDeserializer<String> deserializer = new JsonDeserializer<>(MyEvent.class);
        //deserializer.addTrustedPackages("*");
        return new DefaultKafkaConsumerFactory<>(
                consumerConfigs(),
                new StringDeserializer(),
                new StringDeserializer()
        );
    }

    @Bean
    Map<String, Object> producerConfigs() {
        return Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaSandbox().getBootstrapServers(),
            ProducerConfig.ACKS_CONFIG, "all",
            ProducerConfig.RETRIES_CONFIG, 3,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,StringSerializer.class
        );
    }

    @Bean
    Map<String, Object> consumerConfigs() {

        return Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaSandbox().getBootstrapServers(),
                //ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                //ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false
        );
    }

    @Bean @Lazy // If needed ;-)
    DefaultErrorHandler errorHandler() {
        return kafkaSandbox().getDefaultErrorHandler();
    }
}