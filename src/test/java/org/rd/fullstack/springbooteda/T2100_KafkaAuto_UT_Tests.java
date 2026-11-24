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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.rd.fullstack.springbooteda.config.Application;
import org.rd.fullstack.springbooteda.util.kafka.KafkaConstants;
import org.rd.fullstack.springbooteda.util.kafka.KafkaSandbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.support.ShareAcknowledgment;

@SpringBootTest(classes = Application.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("KAFKA toolbox, annotation, template, listener demo and tests.")
public class T2100_KafkaAuto_UT_Tests {
    private static final Logger logger = LoggerFactory.getLogger(
        T2100_KafkaAuto_UT_Tests.class
    );
    
    private final static String CST_TOPIC_NAME_1  = "T2100-topic-1";
    private final static String CST_TOPIC_GROUP_1 = "T2100-group-1";
    private final static String CST_TOPIC_NAME_2  = "T2100-topic-2";
    private final static String CST_TOPIC_GROUP_2 = "T2100-group-2";

    @Autowired
    private KafkaSandbox kafkaSandbox;

    private AtomicBoolean bSuccess;
    private AtomicInteger msgCount;

    @Test
    @Order(1)
    public void kafkaSandboxListenerDemoTest() {
        assertTrue(kafkaSandbox.isStarted());

        kafkaSandbox.addTopic(CST_TOPIC_NAME_1, 10, (short) 1);
        bSuccess = new AtomicBoolean(false);
        KafkaTemplate<String, String> kafkaTemplate = kafkaSandbox.getKafkaTemplate(CST_TOPIC_NAME_1);

        // Send message - Template.
        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(CST_TOPIC_NAME_1, "Hello Kafka Embedded!");
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                logger.error("Template - Error sending message:", ex);
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

    @Test
    @Order(2)
    @Disabled("Disabled for now, under development.")
    public void kafkaSandboxShareGroupListenerDemoTest() {
        assertTrue(kafkaSandbox.isStarted());
        
        logger.info("***** kafkaSandboxShareGroupListenerDemoTest ***** ");
        kafkaSandbox.addTopic(CST_TOPIC_NAME_2, 
                              CST_TOPIC_NAME_2 + KafkaConstants.CST_TOPIC_DLT_POSTFIX, 
                              10, (short) 1);
        
        // Configure the share group listener to reset offsets to the earliest available message.
        // With API Admin client.
        kafkaSandbox.configureShareGroupOffsetReset(CST_TOPIC_GROUP_2);

        msgCount = new AtomicInteger(0);
        KafkaTemplate<String, String> kafkaTemplate = kafkaSandbox.getKafkaTemplate(CST_TOPIC_NAME_2);

        // Send message - Template.
        kafkaTemplate.send(CST_TOPIC_NAME_2, "ORDER:1001");
        kafkaTemplate.send(CST_TOPIC_NAME_2, "RETRY:1002");
        kafkaTemplate.send(CST_TOPIC_NAME_2, "FINAL:1003");

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> msgCount.get() == 3);
        assertEquals(3, msgCount.get());
    }

    @KafkaListener(topics = CST_TOPIC_NAME_1, groupId = CST_TOPIC_GROUP_1,
                   containerFactory = "kafkaListenerContainerFactory")
    public void listen1(ConsumerRecord<String, String> record, Acknowledgment ack) {
        logger.info("Received message: {}.", record.value());
        ack.acknowledge();
        bSuccess.set(true);
    }

    @KafkaListener(topics = CST_TOPIC_NAME_2, groupId = CST_TOPIC_GROUP_2,
                   containerFactory = "shareKafkaListenerContainerFactory")
    public void listen2(ConsumerRecord<String, String> record, ShareAcknowledgment ack) {

        logger.info("ACK - offset={}", record.offset());
        ack.acknowledge();
        msgCount.incrementAndGet();

        // NACK : nouvelle livraison
        // logger.warn("NACK / RETRY - offset={}", record.offset(), e);
        // ack.release();

        // Rejet définitif : DLT natif
        // logger.error("FINAL / DLT - offset={}", record.offset(), e);
        // logger.error("Unexpected error - offset={}", record.offset(), e);
        // ack.reject();

        // The lock is generally 30 seconds (group.share.record.lock.duration.ms). 
        // If processing exceeds this duration without renewal, the message may be delivered again.
        // ack.renew();
    }
 }
