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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.awaitility.Awaitility;
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
import org.springframework.kafka.support.SendResult;

@SpringBootTest(classes = Application.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("KAFKA toolbox, annotation, template, listener demo and tests.")
public class T2100_KafkaAuto_UT_Tests {
    private static final Logger logger = LoggerFactory.getLogger(
        T2100_KafkaAuto_UT_Tests.class
    );
    
    private final static String CST_TOPIC_NAME  = "T2100-topic-1";
    private final static String CST_TOPIC_GROUP = "T2100-group-1";

    @Autowired
    private KafkaSandbox kafkaSandbox;

    private AtomicBoolean bSuccess;

    public T2100_KafkaAuto_UT_Tests() {
        super();
    }

    @Test
    @Order(1)
    public void kafkaSandboxListenerDemoTest() {
        assertTrue(kafkaSandbox.isStarted());

        kafkaSandbox.addTopic(CST_TOPIC_NAME, 10, (short) 1);
        bSuccess = new AtomicBoolean(false);
        KafkaTemplate<String, String> kafkaTemplate = kafkaSandbox.getKafkaTemplate(CST_TOPIC_NAME);

        // Send message - Template.
        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(CST_TOPIC_NAME, "Hello Kafka Embedded!");
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                logger.error("Template - Error sending messag: {}.", ex);
                fail(ex);
            }

            logger.info(
                "Template - Message sent to topic {}, partition {}, offset {}.",
                result.getRecordMetadata().topic(),
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset()
            );
        });

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilTrue(bSuccess);
        assertTrue(bSuccess.get(), "Message should have been received.");
    }

    @KafkaListener(topics = CST_TOPIC_NAME, groupId = CST_TOPIC_GROUP)
    public void listen(ConsumerRecord<String, String> record, Acknowledgment ack) {
        logger.info("Received message: {}.", record.value());
        ack.acknowledge();
        bSuccess.set(true);
    }
 }