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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.rd.fullstack.springbooteda.config.Application;
import org.rd.fullstack.springbooteda.srv.SmartLifecycleSrv;
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

    public T2200_KafkaManuel_UT_Tests() {
        super();
    }

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
        final String CST_TOPIC_NAME = "T2100-topic-6";

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
            logger.error("Error sending message: {}.", ex);
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
                    logger.error("Error sending message: {}.", ex);
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
}