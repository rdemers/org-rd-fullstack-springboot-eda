/*
 * Copyright 2026; Real Demers.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.example.kafkalifecycle.listener;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.example.kafkalifecycle.health.DownstreamHealth;

/**
 * Sample listener with {@code autoStartup = "false"} so the lifecycle
 * controller decides when to join the consumer group.
 */
@Component
public class OrderListener {

    public static final String LISTENER_ID = "orderListener";

    private static final Logger logger = LoggerFactory.getLogger(OrderListener.class);

    private final DownstreamHealth health;
    private final AtomicLong processed = new AtomicLong();

    public OrderListener(DownstreamHealth health) {
        this.health = health;
    }

    @KafkaListener(
        id = LISTENER_ID,
        topics = "${app.topic:orders}",
        groupId = "${app.group:orders-group}",
        autoStartup = "${app.listener.auto-start:false}"
    )
    public void onOrder(ConsumerRecord<String, String> record, Acknowledgment ack) {
        if (!health.isHealthy()) {
            // Safety net — should not happen because the lifecycle controller
            // stops the listener on outage, but we guard anyway.
            logger.warn("Received record while downstream unhealthy — skipping ack (will redeliver).");
            return;
        }

        logger.info("Processing order key={} offset={} value={}.",
                    record.key(), record.offset(), record.value());
        // ... real business logic here ...
        ack.acknowledge();
        processed.incrementAndGet();
    }

    public long processedCount() {
        return processed.get();
    }
}
