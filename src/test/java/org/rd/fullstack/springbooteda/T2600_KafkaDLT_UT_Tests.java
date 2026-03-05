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

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
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
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest(classes = Application.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("KAFKA toolbox, template and DLT in TRX mode demo and tests.")
public class T2600_KafkaDLT_UT_Tests {
    private static final Logger logger = LoggerFactory.getLogger(
        T2600_KafkaDLT_UT_Tests.class
    );

    private final int CST_NBR_REQUEST_COUNT = 4;

    @Autowired
    private KafkaSandbox kafkaSandbox;

    private AtomicInteger rcvCount;
    private AtomicInteger dltCount;

    public T2600_KafkaDLT_UT_Tests() {
        super();
    }

    @Test
    @Order(1)
    public void kafkaSandboxTrxTemplateDemoTest() {
        assertTrue(kafkaSandbox.isStarted());
        final String CST_TOPIC_NAME     = "T2600-topic-1";
        final String CST_TOPIC_NAME_DLT = "T2600-topic-1-dlt";

        // Create topic.
        kafkaSandbox.addTopic(CST_TOPIC_NAME, CST_TOPIC_NAME_DLT, 10, (short) 1);

        // Get Kafka template.
        KafkaTemplate<String, String> template =
                kafkaSandbox.getKafkaTemplate(CST_TOPIC_NAME, true);

        // Setup message listener.
        rcvCount = new AtomicInteger(0);
        kafkaSandbox.setupMessageListener(CST_TOPIC_NAME, "group-" + UUID.randomUUID(), (record, ack) -> {
            logger.info("Received message: {}.", record.value());

            if (record.key() instanceof String keyStr) {
                if ("key2".equals(keyStr)) {
                    logger.error("Error simulation, DLT transmission (after 3 retries).");
                    throw new IllegalStateException("A simulated exception was generated for the message: " + record.value());
                }
            }
            ack.acknowledge();
            rcvCount.incrementAndGet();
        });

        // Setup message listener - DLT.
        dltCount = new AtomicInteger(0);
        kafkaSandbox.setupMessageListener(CST_TOPIC_NAME_DLT, "group-" + UUID.randomUUID(), (record, ack) -> {
            logger.info("Received message: {}.", record.value());
            ack.acknowledge();
            dltCount.incrementAndGet();
        });

        // Send messages in a transaction.
        template.executeInTransaction(kt -> {
            kt.send(CST_TOPIC_NAME, "key1", "message 1");
            kt.send(CST_TOPIC_NAME, "key2", "message 2");
            kt.send(CST_TOPIC_NAME, "key3", "message 3");
            kt.send(CST_TOPIC_NAME, "key4", "message 4");
            return null;
        });

        // Wait until all messages are processed or timeout.
        await()
            .atMost(10, TimeUnit.SECONDS)
            .until(() -> rcvCount.get() == (CST_NBR_REQUEST_COUNT-1));

        // Assert all requests processed.
        assertEquals((CST_NBR_REQUEST_COUNT-1), rcvCount.get());
        assertEquals(1, dltCount.get());
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