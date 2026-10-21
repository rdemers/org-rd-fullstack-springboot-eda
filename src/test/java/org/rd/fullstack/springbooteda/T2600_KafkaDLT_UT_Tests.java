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