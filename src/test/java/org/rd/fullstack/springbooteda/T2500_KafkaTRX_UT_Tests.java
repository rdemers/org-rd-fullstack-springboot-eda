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
@DisplayName("KAFKA toolbox, producer, template in TRX mode demo and tests.")
public class T2500_KafkaTRX_UT_Tests {
    private static final Logger logger = LoggerFactory.getLogger(
        T2500_KafkaTRX_UT_Tests.class
    );

    private final int CST_NBR_REQUEST_COUNT = 4;

    @Autowired
    private KafkaSandbox kafkaSandbox;

    private AtomicInteger requestCount;

    public T2500_KafkaTRX_UT_Tests() {
        super();
    }

    @Test
    @Order(1)
    public void kafkaSandboxTrxTemplateDemoTest() {
        assertTrue(kafkaSandbox.isStarted());
        final String CST_TOPIC_NAME = "T2500-topic-1";

        // Create topic.
        kafkaSandbox.addTopic(CST_TOPIC_NAME, 10, (short) 1);

        // Get Kafka template.
        KafkaTemplate<String, String> template =
                kafkaSandbox.getKafkaTemplate(CST_TOPIC_NAME, true);

        // Setup message listener.
        requestCount = new AtomicInteger(0);
        kafkaSandbox.setupMessageListener(CST_TOPIC_NAME, "group-" + UUID.randomUUID(), (record, ack) -> {
            logger.info("Received message: {}.", record.value());
            ack.acknowledge();
            requestCount.incrementAndGet();
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
            .until(() -> requestCount.get() == CST_NBR_REQUEST_COUNT);

        // Assert all requests processed.
        assertEquals(CST_NBR_REQUEST_COUNT, requestCount.get());
    }

    @Test
    @Order(2)
    public void kafkaSandboxProducerDemoTest() {
        assertTrue(kafkaSandbox.isStarted());
        final String CST_TOPIC_NAME = "T2500-topic-2";

        // Create topic.
        kafkaSandbox.addTopic(CST_TOPIC_NAME, 10, (short) 1);

        // Setup message listener.
        requestCount = new AtomicInteger(0);
        kafkaSandbox.setupMessageListener(CST_TOPIC_NAME, "group-" + UUID.randomUUID(), (record, ack) -> {
            logger.info("Received message: {}.", record.value());
            ack.acknowledge();
            requestCount.incrementAndGet();
        });

        // Get Kafka producer.
        Producer<String, String> producer =
                kafkaSandbox.getProducer(CST_TOPIC_NAME, true);

        producer.beginTransaction();
        producer.send(new ProducerRecord<>(CST_TOPIC_NAME, "key1", "message 1"));
        producer.send(new ProducerRecord<>(CST_TOPIC_NAME, "key2", "message 2"));
        producer.send(new ProducerRecord<>(CST_TOPIC_NAME, "key3", "message 3"));
        producer.send(new ProducerRecord<>(CST_TOPIC_NAME, "key4", "message 4"));
        producer.commitTransaction();

        // Wait until all messages are processed or timeout.
        await()
            .atMost(10, TimeUnit.SECONDS)
            .until(() -> requestCount.get() == CST_NBR_REQUEST_COUNT);

        // Assert all requests processed.
        assertEquals(CST_NBR_REQUEST_COUNT, requestCount.get());
    }
 }
