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
package org.rd.fullstack.springbooteda.util;

import java.util.Set;

import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

public class KafkaToolBox {

    private static final Logger logger = LoggerFactory.getLogger(KafkaToolBox.class);

    static private boolean                  bStated;
    static private KafkaToolBox             instance = null;
    static private EmbeddedKafkaKraftBroker KafkaBroker = null;
 
    private KafkaToolBox() {} // No direct instanciation.

    public static synchronized KafkaToolBox getInstance() {

        if (instance == null) {
            instance = new KafkaToolBox();
        }
        return instance;        
    }
  
    public synchronized boolean isbStated() {
        return bStated;
    }

    public synchronized void startKafkaBroker() {

        if (KafkaBroker == null) {
            bStated = false;
            KafkaBroker = new EmbeddedKafkaKraftBroker(1, 10, "topic_1", "topic_2");
            logger.info("Kafka cluster created.");
        }

        if (bStated)
            return;

        KafkaBroker.afterPropertiesSet(); // Will start the broker.
        System.setProperty("spring.kafka.bootstrap-servers", KafkaBroker.getBrokersAsString());
        bStated = true;
        
        logger.info("Kafka cluster started.");
    }

    public synchronized void stopKafkaBroker() {

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

    public void addTopics() {
        KafkaBroker.addTopics(new NewTopic("thing1", 10, (short) 1), 
                              new NewTopic("thing2", 15, (short) 1));


        Set<String> topics = KafkaBroker.getTopics();
        logger.info(topics.toString());

        KafkaBroker.

    }
}

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendMessage(String topic, String message) {
        kafkaTemplate.send(topic, message);
    }
}
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaConsumer {

    @KafkaListener(topics = "my-topic", groupId = "my-group")
    public void listen(String message) {
        System.out.println("Message reçu : " + message);
    }
}
