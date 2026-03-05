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
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;

interface TopicHandler<T> {
    T addTopic(String name);
    T addTopic(String name, int partitions, short replicas);

    T addTopic(String name, String dltName);
    T addTopic(String name, String dltName, int partitions, short replicas);
    T addTopic(String name, String dltName, int partitions, short replicas, long retryInterval, long retryAttempts);

    T addTopic(String name, String dltName, int partitions, short replicas, long retryInterval, long retryAttempts, 
            Class<? extends Serializer<?>> keySerializer, Class<? extends Serializer<?>> valueSerializer,
            Class<? extends Deserializer<?>> keyDeserializer, Class<? extends Deserializer<?>> valueDeserializer);

    T addTopic(String name, String dltName, int partitions, short replicas, long retryInterval, long retryAttempts, 
            Class<? extends Serializer<?>> keySerializer, Class<? extends Serializer<?>> valueSerializer,
            Class<? extends Deserializer<?>> keyDeserializer, Class<? extends Deserializer<?>> valueDeserializer,
            Map<String, Object> extraProducerProps, Map<String, Object> extraConsumerProps);

    T addTopics(TopicConfig... topics);
    
    TopicConfig getTopicConfig(String name);
}