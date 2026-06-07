/*
 * Copyright 2026; Real Demers.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.example.kafkalifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

import com.example.kafkalifecycle.health.DownstreamHealth;
import com.example.kafkalifecycle.listener.OrderListener;

/**
 * End-to-end verification of the lifecycle pattern.
 *
 * <p>Scenarios:
 * <ul>
 *   <li>Startup with healthy downstream → listener auto-starts via the
 *       lifecycle controller and consumes messages.</li>
 *   <li>Downstream goes down → reconcile loop stops the listener.</li>
 *   <li>Downstream recovers → reconcile loop restarts the listener and
 *       it resumes consuming.</li>
 * </ul>
 */
@SpringBootTest(properties = {
    "app.listener.reconcile-interval=500",   // fast reconcile for tests
    "app.topic=test-orders",
    "app.group=test-orders-group"
})
@EmbeddedKafka(partitions = 1, topics = "test-orders")
@TestPropertySource(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
class LifecyclePatternIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private KafkaListenerEndpointRegistry registry;

    @Autowired
    private OrderListener listener;

    @Autowired
    private DownstreamHealth health;

    @Test
    void listenerStartsWhenDownstreamHealthyAndStopsOnOutage() {
        // 1. On startup, the ApplicationReadyEvent + healthy downstream
        //    should cause the controller to start the listener.
        await().atMost(Duration.ofSeconds(10))
               .until(() -> container().isRunning());

        // 2. Send a record; it should be processed.
        long before = listener.processedCount();
        kafkaTemplate.send("test-orders", "k1", "payload-1");

        await().atMost(Duration.ofSeconds(10))
               .until(() -> listener.processedCount() == before + 1);

        // 3. Simulate an outage; the reconcile loop should stop the listener.
        health.simulateOutage(true);
        await().atMost(Duration.ofSeconds(10))
               .until(() -> !container().isRunning());

        // 4. Sending now should NOT be processed — the listener is stopped.
        long duringOutage = listener.processedCount();
        kafkaTemplate.send("test-orders", "k2", "payload-2");

        // Give the reconcile loop a few ticks and make sure the counter doesn't move.
        try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        assertThat(listener.processedCount()).isEqualTo(duringOutage);

        // 5. End the outage — the controller restarts the listener.
        health.simulateOutage(false);
        await().atMost(Duration.ofSeconds(10))
               .until(() -> container().isRunning());

        // 6. The message sent during the outage should eventually be consumed
        //    (it stayed in the topic). The listener picks up from its last
        //    committed offset.
        await().atMost(Duration.ofSeconds(15))
               .until(() -> listener.processedCount() >= duringOutage + 1);
    }

    private MessageListenerContainer container() {
        return registry.getListenerContainer(OrderListener.LISTENER_ID);
    }
}
