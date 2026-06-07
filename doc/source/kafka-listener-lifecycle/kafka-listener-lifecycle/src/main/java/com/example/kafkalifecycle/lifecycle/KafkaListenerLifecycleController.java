/*
 * Copyright 2026; Real Demers.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.example.kafkalifecycle.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.kafkalifecycle.health.DownstreamHealth;
import com.example.kafkalifecycle.listener.OrderListener;

/**
 * Reconciles the state of the Kafka listener with the state of the
 * downstream dependency:
 *
 * <ul>
 *   <li>downstream UP + readiness ACCEPTING_TRAFFIC → listener must run</li>
 *   <li>anything else → listener must be stopped</li>
 * </ul>
 *
 * <p>Three triggers drive reconciliation:
 * <ol>
 *   <li>{@link ApplicationReadyEvent} — initial evaluation on startup.</li>
 *   <li>{@link AvailabilityChangeEvent} on {@link ReadinessState} — whenever
 *       a {@link org.springframework.boot.actuate.health.HealthIndicator} in
 *       the readiness group changes, Spring Boot fires this event.</li>
 *   <li>A periodic {@code @Scheduled} tick — defensive reconciliation in
 *       case we miss an event or the downstream comes back on its own.</li>
 * </ol>
 *
 * <p>The controller uses {@code start()} / {@code stop()} (not {@code pause()})
 * so the consumer actually leaves the group when the pod cannot process:
 * other healthy pods pick up the partitions instead of letting lag accumulate
 * on this pod.
 */
@Component
public class KafkaListenerLifecycleController {

    private static final Logger logger = LoggerFactory.getLogger(KafkaListenerLifecycleController.class);

    private final KafkaListenerEndpointRegistry registry;
    private final DownstreamHealth health;
    private final ApplicationAvailability availability;

    /** Lock to serialize reconcile calls from different triggers. */
    private final Object reconcileLock = new Object();

    public KafkaListenerLifecycleController(KafkaListenerEndpointRegistry registry,
                                            DownstreamHealth health,
                                            ApplicationAvailability availability) {
        this.registry = registry;
        this.health = health;
        this.availability = availability;
    }

    // ---------------------------------------------------------------
    // Triggers
    // ---------------------------------------------------------------

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        logger.info("Application ready — evaluating listener startup.");
        health.checkNow();
        reconcile();
    }

    @EventListener
    public void onReadinessChange(AvailabilityChangeEvent<ReadinessState> event) {
        logger.info("Readiness changed to {}.", event.getState());
        reconcile();
    }

    @Scheduled(fixedDelayString = "${app.listener.reconcile-interval:10000}",
               initialDelayString = "${app.listener.reconcile-interval:10000}")
    public void periodicReconcile() {
        health.checkNow();
        reconcile();
    }

    // ---------------------------------------------------------------
    // Reconciliation
    // ---------------------------------------------------------------

    /**
     * Called from multiple triggers; synchronized to avoid racing
     * {@code start()} / {@code stop()} calls on the same container.
     */
    private void reconcile() {
        synchronized (reconcileLock) {
            MessageListenerContainer container = registry.getListenerContainer(OrderListener.LISTENER_ID);
            if (container == null) {
                logger.warn("Listener container '{}' not found; nothing to reconcile.",
                            OrderListener.LISTENER_ID);
                return;
            }

            boolean shouldRun = health.isHealthy()
                && availability.getReadinessState() == ReadinessState.ACCEPTING_TRAFFIC;

            if (shouldRun && !container.isRunning()) {
                logger.info("Starting Kafka listener '{}'.", OrderListener.LISTENER_ID);
                container.start();
            } else if (!shouldRun && container.isRunning()) {
                logger.info("Stopping Kafka listener '{}' (healthy={}, readiness={}).",
                            OrderListener.LISTENER_ID,
                            health.isHealthy(),
                            availability.getReadinessState());
                container.stop();
            }
        }
    }
}
