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
 * 
 * EmbeddedKafka
 * -------------
 * EmbeddedKafka is part of the Spring Kafka testing library and provides 
 * an in-memory, lightweight Kafka broker. It’s an excellent choice for unit 
 * and integration tests because it allows you to run Kafka within the JVM 
 * process of your test, avoiding the need for external Kafka infrastructure.
 * 
 */
package org.rd.fullstack.springbooteda.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

public class KafkaBroker {

    private static final Logger logger = LoggerFactory.getLogger(KafkaBroker.class);

    static private boolean                  bStated;
    static private KafkaBroker              instance = null;
    static private EmbeddedKafkaKraftBroker KafkaBroker = null;
 
    private KafkaBroker() {} // No direct instanciation.

    public static synchronized KafkaBroker getInstance() {

        if (instance == null) {
            instance = new KafkaBroker();
        }
        return instance;        
    }
  
    public synchronized boolean isbStated() {
        return bStated;
    }

    public synchronized void start() {

        if (KafkaBroker == null) {
            bStated = false;
            KafkaBroker = new EmbeddedKafkaKraftBroker(1, 10);
            logger.info("Kafka cluster created.");
        }

        if (bStated)
            return;

        Map<String, String> properties = new HashMap<>();
        properties.put("listeners", 
                     "PLAINTEXT://localhost:9092,REMOTE://10.0.0.20:9093");
        properties.put("advertised.listeners", 
                     "PLAINTEXT://localhost:9092,REMOTE://10.0.0.20:9093");
        properties.put("listener.security.protocol.map", 
                     "PLAINTEXT:PLAINTEXT,REMOTE:PLAINTEXT");

        // Optional: Set broker properties
        // broker 

        KafkaBroker
            //.kafkaPorts(9092)
            .brokerProperties(Collections.singletonMap("port", "9092"))
            //.brokerProperties(properties)
            //.brokerListProperty("spring.kafka.bootstrap-servers")
            .afterPropertiesSet(); // Will start the broker.

        System.setProperty("spring.kafka.bootstrap-servers", KafkaBroker.getBrokersAsString());
        bStated = true;
        
        logger.info("Kafka cluster started.");
    }

    public synchronized void stop() {

        if (KafkaBroker == null)
            return;
        
        if (!bStated)
            return;
        
        KafkaBroker.destroy();
        bStated = false;
        KafkaBroker = null;
        System.clearProperty("spring.kafka.bootstrap-servers");

        logger.info("Kafka cluster stoped.");
    } 

    public void addTopics(NewTopic... newTopics) {

        if (KafkaBroker == null) {
            logger.warn("Kafka broker is not initialized; cannot add topics.");
            return;
        }

        if (newTopics == null || newTopics.length == 0) {
            logger.warn("NewTopics is not nitialized; cannot add topics.");
            return;
        } 
        
        KafkaBroker.addTopics(newTopics);
    }

    public Set<String> getTopics() {

        if (KafkaBroker == null) {
            logger.warn("Kafka broker is not initialized; cannot get topics.");
            return null;
        }

        return KafkaBroker.getTopics();
   }

    public String getBrokerProperties() {

        if (KafkaBroker == null) {
            logger.warn("Kafka broker is not initialized; cannot get properties.");
            return null;
        }

        return KafkaBroker.getBrokersAsString();
   }
}