/*
 * Copyright 2025; Réal Demers.
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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

//https://docs.enterprise.spring.io/spring-kafka/reference/testing.html
//https://www.baeldung.com/spring-kafka
//https://www.baeldung.com/spring-boot-kafka-testing

@Configuration
public class KafkaConfig {

    public KafkaConfig() {
        super();
    }

    @Bean
    public EmbeddedKafkaKraftBroker embeddedKafkaBroker() {
        EmbeddedKafkaKraftBroker embeddedKafkaBroker = 
            new EmbeddedKafkaKraftBroker(1, 10, "topic_1", "topic_2");
        
        embeddedKafkaBroker.afterPropertiesSet(); // Will start the broker.

        System.setProperty("spring.kafka.bootstrap-servers", embeddedKafkaBroker.getBrokersAsString());
        return embeddedKafkaBroker;
    }
}   