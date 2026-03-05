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
package org.rd.fullstack.springbooteda;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.rd.fullstack.springbooteda.config.Application;
import org.rd.fullstack.springbooteda.util.kafka.KafkaSandbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;

@SpringBootTest(classes = Application.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("KAFKA toolbox, template and DLT in TRX mode demo and tests.")
public class T2600_KafkaDLT_UT_Tests {
    private static final Logger logger = LoggerFactory.getLogger(
        T2600_KafkaDLT_UT_Tests.class
    );

    private final int CST_NBR_REQUEST_VALID_COUNT = 4;
    private final int CST_NBR_REQUEST_DLT_COUNT   = 1;

    private final String CST_TOPIC_NAME_1     = "T2600-topic-1";
    private final String CST_TOPIC_NAME_DLT_1 = "T2600-topic-1-dlt";

    private final String CST_TOPIC_NAME_2     = "T2600-topic-2";
    private final String CST_TOPIC_NAME_DLT_2 = "T2600-topic-2-dlt";
    
    private final String CST_TOPIC_GROUP_NRM = "nrm-group";
    private final String CST_TOPIC_GROUP_DLT = "dlt-group";

    @Autowired
    private KafkaSandbox kafkaSandbox;

    private AtomicInteger rcvCount;
    private AtomicInteger dltCount;

    public T2600_KafkaDLT_UT_Tests() {
        super();
    }

    @Test
    @Order(1)
    public void kafkaSandboxDltAnnotationListenerDemoTest() {
        assertTrue(kafkaSandbox.isStarted());

        // Create topic.
        kafkaSandbox.addTopic(CST_TOPIC_NAME_1, CST_TOPIC_NAME_DLT_1, 10, (short) 1);

        // Get Kafka template.
        KafkaTemplate<String, String> template =
                kafkaSandbox.getKafkaTemplate(CST_TOPIC_NAME_1, true);

        rcvCount = new AtomicInteger(0);
        dltCount = new AtomicInteger(0);

        // Setup message listener.
        kafkaSandbox.setupMessageListener(CST_TOPIC_NAME_1, CST_TOPIC_GROUP_NRM, (record, ack) -> {
  
            if (record.key() instanceof String keyStr) {
                if ("key2".equals(keyStr)) {
                    logger.info("DLT Simulation ...");
                    throw new IllegalStateException("A simulated exception was generated for the message: " + record.value());
                }
            }

            logger.info("NRM - Received message: {}.", record.value());
            ack.acknowledge();
            rcvCount.incrementAndGet();
        });

        // Send messages in a transaction.
        template.executeInTransaction(kt -> {
            kt.send(CST_TOPIC_NAME_1, "key1", "message 1");
            kt.send(CST_TOPIC_NAME_1, "key2", "message 2");
            kt.send(CST_TOPIC_NAME_1, "key3", "message 3");
            kt.send(CST_TOPIC_NAME_1, "key4", "message 4");
            kt.send(CST_TOPIC_NAME_1, "key5", "message 5");
            return null;
        });

        // Wait until all messages are processed or timeout.
        await()
            .atMost(30, TimeUnit.SECONDS)
            .until(() -> ((rcvCount.get() == CST_NBR_REQUEST_VALID_COUNT) && 
                          (dltCount.get() == CST_NBR_REQUEST_DLT_COUNT)));

        // Assert all requests processed.
        assertEquals(CST_NBR_REQUEST_VALID_COUNT, rcvCount.get());
        assertEquals(CST_NBR_REQUEST_DLT_COUNT, dltCount.get() );
    }

    @Test
    @Order(2)
    public void kafkaSandboxDltMessageListenerDemoTest() {
        assertTrue(kafkaSandbox.isStarted());

        // Create topic.
        kafkaSandbox.addTopic(CST_TOPIC_NAME_2, CST_TOPIC_NAME_DLT_2, 10, (short) 1);

        // Get Kafka template.
        KafkaTemplate<String, String> template =
                kafkaSandbox.getKafkaTemplate(CST_TOPIC_NAME_2, true);

        rcvCount = new AtomicInteger(0);
        dltCount = new AtomicInteger(0);

        // Setup message listener.
        kafkaSandbox.setupMessageListener(CST_TOPIC_NAME_2, CST_TOPIC_GROUP_NRM, (record, ack) -> {
  
            if (record.key() instanceof String keyStr) {
                if ("key2".equals(keyStr)) {
                    logger.info("DLT Simulation ...");
                    throw new IllegalStateException("A simulated exception was generated for the message: " + record.value());
                }
            }

            logger.info("NRM - Received message: {}.", record.value());
            ack.acknowledge();
            rcvCount.incrementAndGet();
        });

        // Setup message listener - DLT.
        kafkaSandbox.setupMessageListener(CST_TOPIC_NAME_DLT_2, CST_TOPIC_GROUP_DLT, (record, ack) -> {
            logger.info("DLT - Received message: {}.", record.value());
            dltCount.incrementAndGet();
            ack.acknowledge();
        });

        // Send messages in a transaction.
        template.executeInTransaction(kt -> {
            kt.send(CST_TOPIC_NAME_2, "key1", "message 1");
            kt.send(CST_TOPIC_NAME_2, "key2", "message 2");
            kt.send(CST_TOPIC_NAME_2, "key3", "message 3");
            kt.send(CST_TOPIC_NAME_2, "key4", "message 4");
            kt.send(CST_TOPIC_NAME_2, "key5", "message 5");
            return null;
        });

        // Wait until all messages are processed or timeout.
        await()
            .atMost(30, TimeUnit.SECONDS)
            .until(() -> ((rcvCount.get() == CST_NBR_REQUEST_VALID_COUNT) && 
                          (dltCount.get() == CST_NBR_REQUEST_DLT_COUNT)));

        // Assert all requests processed.
        assertEquals(CST_NBR_REQUEST_VALID_COUNT, rcvCount.get());
        assertEquals(CST_NBR_REQUEST_DLT_COUNT, dltCount.get() );
    }

    @KafkaListener(topics = CST_TOPIC_NAME_DLT_1, groupId = CST_TOPIC_GROUP_DLT)
    public void listen(ConsumerRecord<String, String> record, Acknowledgment ack) {
        logger.info("DLT - Received message: {}.", record.value());
        dltCount.incrementAndGet();
        ack.acknowledge();
    }
 }

 /*
    Other possibility: https://www.baeldung.com/kafka-spring-dead-letter-queue

    package org.rd.fullstack.springbooteda.listener;

    import java.util.Random;

    import org.apache.kafka.clients.consumer.ConsumerRecord;
    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;
    import org.springframework.kafka.annotation.KafkaListener;
    import org.springframework.kafka.annotation.DltHandler;
    import org.springframework.kafka.annotation.RetryableTopic;
    import org.springframework.kafka.support.Acknowledgment;
    import org.springframework.kafka.support.KafkaHeaders;
    import org.springframework.messaging.handler.annotation.Header;
    import org.springframework.stereotype.Component;
    import org.springframework.transaction.annotation.Transactional;

    @Component
    public class KafkaTransactionalListener {

        private static final Logger log = LoggerFactory.getLogger(KafkaTransactionalListener.class);

        private final Random random = new Random();

        @Transactional
        @KafkaListener(
            topics       = "${kafka.topic.name}",
            groupId      = "${kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
        )
        public void listen(
                ConsumerRecord<String, String> record,
                Acknowledgment ack,
                @Header(KafkaHeaders.RECEIVED_TOPIC)    String topic,
                @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                @Header(KafkaHeaders.OFFSET)             long offset) {

            log.info("Message reçu -> topic: {}, partition: {}, offset: {}, valeur: {}",
                    topic, partition, offset, record.value());

            int result = random.nextInt(5) + 1; // 1 à 5
            log.info("Résultat du random : {}", result);

            if (result == 1) {
                // Simule une erreur métier -> le message ira en DLT après les retries.
                log.error("Random == 1 : simulation d'une erreur, envoi en DLT.");
                throw new IllegalStateException(
                    "Erreur simulée pour le message offset=" + offset);
            }

            // Succès : on acquitte le message manuellement.
            log.info("Random == {} : traitement réussi, ACK du message.", result);
            ack.acknowledge();
        }

        // Handler DLT : appelé quand le message atterrit dans le Dead Letter Topic.
        @DltHandler
        public void handleDlt(
                ConsumerRecord<String, String> record,
                @Header(KafkaHeaders.RECEIVED_TOPIC)    String topic,
                @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                @Header(KafkaHeaders.OFFSET)             long offset) {

            log.warn("=== MESSAGE EN DLT ===");
            log.warn("Topic DLT   : {}", topic);
            log.warn("Partition   : {}", partition);
            log.warn("Offset      : {}", offset);
            log.warn("Valeur      : {}", record.value());
            log.warn("======================");

            // Ici vous pouvez : persister en base, alerter, notifier, etc.
        }
    }
*/