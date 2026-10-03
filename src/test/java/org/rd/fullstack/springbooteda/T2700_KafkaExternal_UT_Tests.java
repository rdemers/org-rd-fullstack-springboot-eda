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

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.rd.fullstack.springbooteda.config.Application;
import org.rd.fullstack.springbooteda.srv.SmartLifecycleSrv;
import org.rd.fullstack.springbooteda.util.kafka.KafkaConstants;
import org.rd.fullstack.springbooteda.util.kafka.KafkaSandbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Demonstrates that a KafkaSandbox configured as an <em>external</em> broker
 * ({@link org.rd.fullstack.springbooteda.util.kafka.ExternalBrokerLifecycle}) can be
 * pointed at the bootstrap URL of the <em>embedded</em> broker that the application
 * already started ({@link org.rd.fullstack.springbooteda.util.kafka.EmbeddedBrokerLifecycle}).
 *
 * <p>Both sandboxes then talk to the very same physical broker, which lets us show
 * full interoperability: a topic created or a message produced through one sandbox is
 * visible/consumable through the other.</p>
 *
 * <p>The production/consumption assertions are modelled on
 * {@code T2200_KafkaManuel_UT_Tests}.</p>
 */
@SpringBootTest(classes = Application.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("KAFKA external sandbox bound to the embedded broker URL — demo and tests.")
public class T2700_KafkaExternal_UT_Tests {

    private static final Logger logger =
        LoggerFactory.getLogger(T2700_KafkaExternal_UT_Tests.class);

    private static final String CST_TOPIC_EXTERNAL = "T2700-external-topic";
    private static final String CST_TOPIC_INTEROP  = "T2700-interop-topic";

    // The embedded sandbox started by the application (autoStart=true).
    @Autowired
    private KafkaSandbox kafkaSandbox;

    @Autowired
    private SmartLifecycleSrv smartLifecycleService;

    // A second sandbox we build ourselves, in "external" mode, reusing the embedded URL.
    private KafkaSandbox externalSandbox;

    private AtomicBoolean bSuccess;

    @BeforeAll
    void startExternalSandbox() {
        assertTrue(kafkaSandbox.isStarted(),
            "The embedded sandbox must be started by the application.");

        // Reuse the URL of the embedded broker as the bootstrap-servers of the external sandbox.
        final String embeddedUrl = kafkaSandbox.getBootstrapServers();
        logger.info("Embedded broker URL reused for the external sandbox: {}.", embeddedUrl);

        externalSandbox = KafkaSandbox.builder()
            .bootstrapServers(embeddedUrl)            // -> ExternalBrokerLifecycle
            .autoCreateTopic(true)    // create missing topics on the shared broker
            .addTopic(CST_TOPIC_EXTERNAL, 10, (short) 1)
            .addTopic(KafkaConstants.CST_DEFAULT_DLT) // needed by setupMessageListener's error handler
            .autoStart(true)
            .build();
    }

    @AfterAll
    void stopExternalSandbox() {
        // Closes the external sandbox (its AdminClient/producers/listeners) WITHOUT
        // touching the embedded broker, which is owned by the Spring context.
        if (externalSandbox != null)
            externalSandbox.close();
    }

    @Test
    @Order(1)
    public void externalSandboxConnectsToEmbeddedBrokerTest() {
        assertTrue(externalSandbox.isStarted(), "The external sandbox must be started.");
        assertEquals(kafkaSandbox.getBootstrapServers(), externalSandbox.getBootstrapServers(),
            "The external sandbox must use the very same bootstrap URL as the embedded broker.");
    }

    @Test
    @Order(2)
    public void topicCreatedByExternalIsVisibleFromEmbeddedTest() throws Exception {
        // CST_TOPIC_EXTERNAL was created on the shared broker by the external sandbox at start().
        // We query the broker's LIVE metadata through the EMBEDDED sandbox's AdminClient.
        // (Note: kafkaSandbox.getTopics() reflects an in-memory set local to the embedded
        //  broker helper and would not show topics created by another AdminClient.)
        Set<String> brokerTopics = kafkaSandbox.getAdminClient()
            .listTopics().names().get(10, TimeUnit.SECONDS);
        assertNotNull(brokerTopics);
        logger.info("Topics on the shared broker (via the embedded AdminClient): {}.", brokerTopics);

        assertTrue(brokerTopics.contains(CST_TOPIC_EXTERNAL),
            "A topic created through the external sandbox must be visible on the shared broker "
            + "from the embedded sandbox's AdminClient.");
    }

    @Test
    @Order(3)
    public void externalProducerConsumerRoundTripTest() {
        assertTrue(externalSandbox.isStarted());

        bSuccess = new AtomicBoolean(false);
        externalSandbox.setupMessageListener(CST_TOPIC_EXTERNAL, (record, ack) -> {
            if (!smartLifecycleService.isRunning())
                return;

            logger.info("[external] received message: {}.", record.value());
            ack.acknowledge();
            bSuccess.set(true);
        });

        externalSandbox
            .getProducer(CST_TOPIC_EXTERNAL)
            .send(new ProducerRecord<>(CST_TOPIC_EXTERNAL, "key1", "Hello from the external sandbox!"));

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilTrue(bSuccess);
        assertTrue(bSuccess.get(), "The message must be produced and consumed through the external sandbox.");
    }

    @Test
    @Order(4)
    public void produceExternallyConsumeEmbeddedTest() {
        assertTrue(externalSandbox.isStarted());
        assertTrue(kafkaSandbox.isStarted());

        // Same physical broker: register the interop topic in BOTH sandboxes (separate
        // registries). The embedded sandbox creates it first (so its in-memory topic set
        // stays consistent); the external sandbox then sees it via a live AdminClient
        // query and skips creation (idempotent).
        kafkaSandbox.addTopic(CST_TOPIC_INTEROP, 10, (short) 1);    // creates on the broker + tracks it
        externalSandbox.addTopic(CST_TOPIC_INTEROP, 10, (short) 1); // already exists (live) -> skip-create

        bSuccess = new AtomicBoolean(false);
        kafkaSandbox.setupMessageListener(CST_TOPIC_INTEROP, (record, ack) -> {
            if (!smartLifecycleService.isRunning())
                return;

            logger.info("[embedded] received message from the external producer: {}.", record.value());
            ack.acknowledge();
            bSuccess.set(true);
        });

        externalSandbox
            .getKafkaTemplate(CST_TOPIC_INTEROP)
            .send(CST_TOPIC_INTEROP, "key1", "Produced externally, consumed by the embedded sandbox!");

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilTrue(bSuccess);
        assertTrue(bSuccess.get(),
            "A message produced via the external sandbox must be consumed by the embedded sandbox.");
    }
}
