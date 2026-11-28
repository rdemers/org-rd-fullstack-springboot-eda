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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.support.ShareAcknowledgment;

@SpringBootTest(classes = Application.class)
@Import(T2100_KafkaAuto_UT_Tests.KafkaTestConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("KAFKA toolbox, annotation, template, listener demo and tests.")
public class T2100_KafkaAuto_UT_Tests {

    private static final Logger logger = LoggerFactory.getLogger(T2100_KafkaAuto_UT_Tests.class);

    private static final String CST_TOPIC_NAME_1  = "T2100-topic-1";
    private static final String CST_TOPIC_GROUP_1 = "T2100-group-1";

    private static final String CST_TOPIC_NAME_2  = "T2100-topic-2";
    private static final String CST_TOPIC_GROUP_2 = "T2100-group-2";

    private static final String CST_LISTENER_1 = "T2100-group-1-listener";
    private static final String CST_LISTENER_2 = "T2100-group-2-listener";

    @Autowired
    private KafkaSandbox kafkaSandbox;

    @Autowired
    private KafkaListenerEndpointRegistry listenerRegistry;

    @Autowired
    private KafkaTestState state;

    @Test
    @Order(1)
    void kafkaSandboxListenerDemoTest() {

        assertTrue(kafkaSandbox.isStarted());
        state.reset();

        kafkaSandbox.addTopic(CST_TOPIC_NAME_1, 10, (short) 1);
        KafkaTemplate<String, String> kafkaTemplate = kafkaSandbox.getKafkaTemplate(CST_TOPIC_NAME_1);
        CompletableFuture<SendResult<String, String>> future =
            kafkaTemplate.send(CST_TOPIC_NAME_1,"Hello Kafka Embedded!");

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                logger.error("Template - Error sending message", ex);
                fail(ex);
            }

        logger.info("Template - Message sent to topic {}, partition {}, offset {}.",
            result.getRecordMetadata().topic(),
            result.getRecordMetadata().partition(),
            result.getRecordMetadata().offset());
        });

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilTrue(state.success());
        assertTrue(state.success().get(),"Message should have been received.");
    }

    @Test
    @Order(2)
    void kafkaSandboxShareGroupListenerDemoTest() {

        assertTrue(kafkaSandbox.isStarted());
        state.reset();

        // configure the share group to start from earliest offset (default = latest).
        // This is important for testing, otherwise the listener will not receive any messages if the topic already has messages.
        //
        // Messages sent before initialization: With the default (latest), the share group sets its start offset at the end 
        // of the partition when the first consumer connects. Because this state is initialized on the broker only once the 
        // consumer is active, any messages published before your listener finishes starting up and joining the group are skipped.
        //
        // The Only 2 Scenarios Where Messages Are Missed (latest)
        // - Initial group creation: When your consumer connects for the first time with a brand-new group.id, 
        //   any messages published before that exact moment are skipped.
        // - Adding new partitions to an existing topic: If the topic's partition count is increased later, 
        //   the new partition has no prior state for your share group. It will initialize at latest, potentially 
        //   missing messages published to that new partition before the consumer begins reading from it.
        kafkaSandbox.addTopic(CST_TOPIC_NAME_2, CST_TOPIC_NAME_2 + KafkaConstants.CST_TOPIC_DLT_POSTFIX, 10, (short) 1);
        kafkaSandbox.configureShareGroupOffsetReset(CST_TOPIC_GROUP_2); 
        MessageListenerContainer container = listenerRegistry.getListenerContainer(CST_LISTENER_2);
        assertTrue(container != null);
        if (! container.isRunning())
            container.start();
 
        KafkaTemplate<String, String> kafkaTemplate = kafkaSandbox.getKafkaTemplate(CST_TOPIC_NAME_2);
        kafkaTemplate.send(CST_TOPIC_NAME_2, "ACK:1001");
        kafkaTemplate.send(CST_TOPIC_NAME_2, "RELEASE:1002");
        kafkaTemplate.send(CST_TOPIC_NAME_2, "REJECT:1003");
        kafkaTemplate.send(CST_TOPIC_NAME_2, "RENEW:1004");

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> state.messageCount().get() == 4);
        assertEquals(4, state.messageCount().get());
    }

    static class KafkaTestState {
        private final AtomicBoolean success = new AtomicBoolean(false);
        private final AtomicInteger messageCount = new AtomicInteger(0);

        AtomicBoolean success() {
            return success;
        }

        AtomicInteger messageCount() {
            return messageCount;
        }

        void reset() {
            success.set(false);
            messageCount.set(0);
        }
    }

    static class KafkaTestListeners {
        private final KafkaTestState state;

        KafkaTestListeners(KafkaTestState state) {
            this.state = state;
        }

        @KafkaListener(id = CST_LISTENER_1, topics = CST_TOPIC_NAME_1, groupId = CST_TOPIC_GROUP_1,
                       containerFactory = "kafkaListenerContainerFactory")
        public void listenClassic(ConsumerRecord<String, String> record, Acknowledgment ack) {

            logger.info("Received message: {}", record.value());
            
            state.success().set(true);
            ack.acknowledge();
        }

        @KafkaListener(id = CST_LISTENER_2, topics = CST_TOPIC_NAME_2, groupId = CST_TOPIC_GROUP_2,
                       containerFactory = "shareKafkaListenerContainerFactory", autoStartup = "false")
        public void listenShared(ConsumerRecord<String, String> record, ShareAcknowledgment ack) {

            logger.info("Received shared message at offset {}", record.offset());
 
            state.messageCount().incrementAndGet();
            String value = record.value();

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
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class KafkaTestConfig {
        @Bean
        KafkaTestState kafkaTestState() {
            return new KafkaTestState();
        }
 
        @Bean
        KafkaTestListeners kafkaTestListeners(KafkaTestState state) {
            return new KafkaTestListeners(state);
        }
    }
}