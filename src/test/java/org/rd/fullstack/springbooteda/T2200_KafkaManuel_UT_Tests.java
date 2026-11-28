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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.rd.fullstack.springbooteda.config.Application;
import org.rd.fullstack.springbooteda.srv.SmartLifecycleSrv;
import org.rd.fullstack.springbooteda.util.kafka.KafkaConstants;
import org.rd.fullstack.springbooteda.util.kafka.KafkaSandbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest(classes = Application.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("KAFKA toolbox, producer, consumer demo and tests.")
public class T2200_KafkaManuel_UT_Tests {
    private static final Logger logger = LoggerFactory.getLogger(
        T2200_KafkaManuel_UT_Tests.class
    );

    @Autowired
    private KafkaSandbox kafkaSandbox;

    @Autowired
    private SmartLifecycleSrv smartLifecycleService;

    private AtomicBoolean bSuccess;
    private AtomicInteger messageCount;

    @Test
    @Order(1)
    public void kafkaSandboxTopicsDemoTest() {
        assertTrue(kafkaSandbox.isStarted());
        final String CST_TOPIC_NAME_1 = "T2200-topic-1";
        final String CST_TOPIC_NAME_2 = "T2200-topic-2";
        final String CST_TOPIC_NAME_3 = "T2200-topic-3";
        final String CST_TOPIC_NAME_4 = "T2200-topic-4";
        final String CST_TOPIC_NAME_5 = "T2200-topic-5";

        // Create topics.
        kafkaSandbox.addTopic(CST_TOPIC_NAME_1, 10, (short) 1);
        kafkaSandbox.addTopic(CST_TOPIC_NAME_2, 10, (short) 1);
        kafkaSandbox.addTopic(CST_TOPIC_NAME_3, 10, (short) 1);
        kafkaSandbox.addTopic(CST_TOPIC_NAME_4, 10, (short) 1);
        kafkaSandbox.addTopic(CST_TOPIC_NAME_5, 10, (short) 1);

        Set<String> topics = kafkaSandbox.getTopics();
        assertNotNull(topics);
        logger.info(topics.toString());
    }

    @Test
    @Order(2)
    public void kafkaSandboxProducerDemoTest() {
        assertTrue(kafkaSandbox.isStarted());
        final String CST_TOPIC_NAME = "T2200-topic-6";

        // Create topics.
        kafkaSandbox.addTopic(CST_TOPIC_NAME, 10, (short) 1);

        Producer<String, String> producer = kafkaSandbox.getProducer(CST_TOPIC_NAME);
        Future<RecordMetadata> future = producer.send(
            new ProducerRecord<>(
                CST_TOPIC_NAME,
                "key1",
                "Hello Kafka Embedded!"
            )
        );

        try {
            RecordMetadata metadata = future.get();
            logger.info(
                "Producer - Message sent to topic {}, partition {}, offset {}.",
                metadata.topic(),
                metadata.partition(),
                metadata.offset()
            );
        } catch (ExecutionException | InterruptedException ex) {
            logger.error("Error sending message:", ex);
            fail(ex);
        }
    }

    @Test
    @Order(3)
    public void kafkaSandboxTemplateDemoTest() {
        assertTrue(kafkaSandbox.isStarted());
        final String CST_TOPIC_NAME = "T2200-topic-7";

        // Create topics.
        kafkaSandbox.addTopic(CST_TOPIC_NAME, 10, (short) 1);

        KafkaTemplate<String, String> template =
            kafkaSandbox.getKafkaTemplate(CST_TOPIC_NAME);

        template
            .send(CST_TOPIC_NAME, "key1", "Hello Kafka Embedded!")
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    logger.error("Error sending message:", ex);
                    fail(ex);
                }

                logger.info(
                    "Message sent to topic {}, partition {}, offset {}.",
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset()
                );
            });
    }

    @Test
    @Order(4)
    public void kafkaSandboxMessageListenerDemoTest() {
        assertTrue(kafkaSandbox.isStarted());
        final String CST_TOPIC_NAME = "T2200-topic-8";

        // Create a topic.
        kafkaSandbox.addTopic(CST_TOPIC_NAME, 10, (short) 1);

        bSuccess = new AtomicBoolean(false);
        kafkaSandbox.setupMessageListener(CST_TOPIC_NAME, (record, ack) -> {

            if (!smartLifecycleService.isRunning()) 
                return;

            logger.info("Received message: {}.", record.value());
            ack.acknowledge();
            bSuccess.set(true);
        });

        kafkaSandbox
            .getProducer(CST_TOPIC_NAME)
            .send(new ProducerRecord<>(CST_TOPIC_NAME, "key1", "Hello Kafka!"));

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilTrue(bSuccess);
        assertTrue(bSuccess.get(), "Message should have been received.");
    }

    @Test
    @Order(5)
    public void kafkaSandboxContainerListenerDemoTest() {
        assertTrue(kafkaSandbox.isStarted());
        final String CST_TOPIC_NAME = "T2200-topic-9";

        // Create topics.
        kafkaSandbox.addTopic(CST_TOPIC_NAME, 10, (short) 1);

        bSuccess = new AtomicBoolean(false);
        kafkaSandbox.setupMessageListener(CST_TOPIC_NAME, (record, ack) -> {

            if (!smartLifecycleService.isRunning()) 
                return;

            logger.info("Received message: {}.", record.value());
            ack.acknowledge();
            bSuccess.set(true);
        });

        kafkaSandbox
            .getKafkaTemplate(CST_TOPIC_NAME)
            .send(CST_TOPIC_NAME, "key1", "Hello Container Listener!");

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilTrue(bSuccess);
        assertTrue(bSuccess.get(), "Message should have been received.");
    }
    
    @Test
    @Order(6)
    @Disabled("Disabled until completed.")
    public void kafkaSandboxShareContainerListenerDemoTest() {
        assertTrue(kafkaSandbox.isStarted());
        final String CST_TOPIC_NAME = "T2200-topic-10";

        // Create topics.
        kafkaSandbox.addTopic(CST_TOPIC_NAME,
            CST_TOPIC_NAME + KafkaConstants.CST_TOPIC_DLT_POSTFIX,10, (short) 1);
        kafkaSandbox.configureShareGroupOffsetReset(CST_TOPIC_NAME); 

        messageCount = new AtomicInteger(0);
        kafkaSandbox.setupShareMessageListener(CST_TOPIC_NAME, (record, ack, consumer) -> {

            logger.info("Message : [offset={}, partition={}, thread={}, clientInstanceId={}, topics={}]",
                    record.offset(),
                    record.partition(),
                    Thread.currentThread().getName(),
                    consumer.clientInstanceId(Duration.ofSeconds(1)),
                    consumer.subscription()
            );
 
            messageCount.incrementAndGet();
            String value = (String) record.value();

            if (value.startsWith("ACK")) {
                logger.info("ACK - offset={}", record.offset());
                ack.acknowledge(); // Acknowledge the message, no further delivery.
            } else if (value.startsWith("RELEASE")) {
                logger.warn("NACK / RETRY - offset={}", record.offset());
                ack.release(); // Release the message, it will be redelivered to the same 
                               // or another consumer in the group.
            } else if (value.startsWith("REJECT")) {
                logger.error("FINAL / DLT - offset={}", record.offset());
                ack.reject(); // Reject the message, 
                              // it will be sent to the DLT (Dead Letter Topic) if configured, 
                              // otherwise it will be discarded.
            } else if (value.startsWith("RENEW")) {
                logger.info("RENEW - offset={}", record.offset());
                ack.renew(); // Renew the lock on the message, preventing it from being 
                             // redelivered to another consumer in the group.
            } else {
                logger.error("Unexpected message - offset={}", record.offset());
                ack.reject(); // See previous comment about DLT. This is a permanent rejection.
            }
        });

        KafkaTemplate<String, String> kafkaTemplate = kafkaSandbox.getKafkaTemplate(CST_TOPIC_NAME);
        kafkaTemplate.send(CST_TOPIC_NAME, "ACK:1001");
        kafkaTemplate.send(CST_TOPIC_NAME, "RELEASE:1002");
        kafkaTemplate.send(CST_TOPIC_NAME, "REJECT:1003");
        kafkaTemplate.send(CST_TOPIC_NAME, "RENEW:1004");

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> messageCount.get() == 4);
        assertEquals(4, messageCount.get());
    }
}