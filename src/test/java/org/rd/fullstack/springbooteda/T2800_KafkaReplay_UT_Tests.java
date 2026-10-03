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
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.rd.fullstack.springbooteda.config.Application;
import org.rd.fullstack.springbooteda.util.kafka.KafkaSandbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.SendResult;

/**
 * Demonstrates that a KafkaSandbox configured as a 
 * replay engine to fix production bugs.
 */
@SpringBootTest(classes = Application.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("KAFKA sandbox replay from a file — demo and tests.")
public class T2800_KafkaReplay_UT_Tests {

    private static final Logger logger =
        LoggerFactory.getLogger(T2800_KafkaReplay_UT_Tests.class);

    private int CST_NBR_REQUEST_COUNT = 0;

    private static final String CST_TOPIC_REPLAY = "T2800-replay-topic";
    private static final String CST_TOPIC_GROUP  = "T2800-replay-topic";
    private static final String CST_FILE_REPLAY  = "batch-t2800-replay.trx";

    @Autowired
    private KafkaSandbox kafkaSandbox;

    private AtomicInteger requestCount;

    @Test
    @Order(1)
    public void simpleFileReplayTest() {

        assertTrue(kafkaSandbox.isStarted());

        kafkaSandbox.addTopic(CST_TOPIC_REPLAY, 10, (short) 1);
        requestCount = new AtomicInteger(0);
        KafkaTemplate<String, String> kafkaTemplate = kafkaSandbox.getKafkaTemplate(CST_TOPIC_REPLAY);

        Path path = null;
        try {
            ClassPathResource resource = new ClassPathResource(CST_FILE_REPLAY);
            path = resource.getFile().toPath();
        } catch (IOException ex) {
            logger.error("IOException - Error message: {}.", ex);
            fail(ex);
        }
    
        try (Stream<String> messages = Files.lines(path)) {
            messages.forEach(message -> {
                CompletableFuture<SendResult<String, String>> future = 
                    kafkaTemplate.send(CST_TOPIC_REPLAY, message);
                    
                future.whenComplete((result, ex) -> {
                    if (ex != null) {
                        logger.error("Template - Error sending message: {}.", ex);
                        fail(ex);
                    }
                });
                CST_NBR_REQUEST_COUNT++;
            });
        } catch (IOException ex) {
            logger.error("IOException - Error message: {}.", ex);
            fail(ex);
        }

        logger.info("Requests count: {}.", CST_NBR_REQUEST_COUNT);

        // Wait until all messages are processed or timeout.
        await()
            .atMost(60, TimeUnit.SECONDS)
            .until(() -> requestCount.get() == CST_NBR_REQUEST_COUNT);
    }

    @KafkaListener(topics = CST_TOPIC_REPLAY, groupId = CST_TOPIC_GROUP)
    public void listen(ConsumerRecord<String, String> record, Acknowledgment ack) {
        logger.info("Received replay message: {}.", record.value());
        ack.acknowledge();
        logger.info("records count: {}.", requestCount.incrementAndGet());
    }
}