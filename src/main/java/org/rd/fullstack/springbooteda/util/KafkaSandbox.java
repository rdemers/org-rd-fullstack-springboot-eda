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

import java.util.Set;

import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

public class KafkaSandbox {

    private static final Logger logger = LoggerFactory.getLogger(KafkaSandbox.class);

    private boolean bStarted;
    private int count;
    private int partitions;

    private EmbeddedKafkaKraftBroker KafkaBroker;
 
    public KafkaSandbox() {
        super();
        this.bStarted = false;
        this.count = 1;
        this.partitions = 10;
        KafkaBroker = new EmbeddedKafkaKraftBroker(this.count, this.partitions);
    }

    public KafkaSandbox(int count, int partitions) {
        super();

        if ((count  < 0) || (partitions < 0))
            throw new IllegalArgumentException("Count and Partitions must be greater than zero.");
         
        this.bStarted = false;
        this.count = count;
        this.partitions = partitions;
        KafkaBroker = new EmbeddedKafkaKraftBroker(this.count, this.partitions);
    }
  
    public synchronized boolean isStarted() {
        return this.bStarted;
    }

    public int getCount() {
        return this.count;
    }

    public int getPartitions() {
        return this.partitions;
    }

    public synchronized void start() {
        if (this.bStarted)
            return;

        KafkaBroker.afterPropertiesSet(); // Will start the broker.

        System.setProperty("spring.kafka.bootstrap-servers", KafkaBroker.getBrokersAsString());
        this.bStarted = true;

        logger.info("Kafka cluster started.");
    }

    public synchronized void stop() {
        
        if (!this.bStarted)
            return;
        
        System.clearProperty("spring.kafka.bootstrap-servers");
        this.KafkaBroker.destroy();
        this.bStarted = false;
        KafkaBroker = new EmbeddedKafkaKraftBroker(this.count, this.partitions);

        logger.info("Kafka cluster stopped.");
    } 

    public void addTopics(NewTopic... newTopics) {
        if (newTopics == null || newTopics.length == 0)
            throw new IllegalArgumentException("At least one topic must be provided.");
        
        this.KafkaBroker.addTopics(newTopics);
        logger.info("Topics added to Kafka broker.");
    }

    public void addTopic(String name, int numPartitions, short replicationFactor) {

        if ((name == null) || (name.length() == 0) || 
            (numPartitions <= 0) || (replicationFactor <= 0))
            throw new IllegalArgumentException("Invalid topic parameters.");
        
        this.KafkaBroker.addTopics(new NewTopic(name, numPartitions, replicationFactor));
        logger.info("Topic added to Kafka broker.");
    }

    public Set<String> getTopics() {
        return this.KafkaBroker.getTopics();
    }

    public String getBrokerProperties() {
        return this.KafkaBroker.getBrokersAsString();
    }
}