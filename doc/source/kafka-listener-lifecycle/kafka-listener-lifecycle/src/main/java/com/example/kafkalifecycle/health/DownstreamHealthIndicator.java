/*
 * Copyright 2026; Real Demers.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package com.example.kafkalifecycle.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Exposes {@link DownstreamHealth} as a Spring Boot {@link HealthIndicator}.
 * Registered under the name {@code "downstream"} — wire it into the readiness
 * group via {@code management.endpoint.health.group.readiness.include}.
 */
@Component("downstream")
public class DownstreamHealthIndicator implements HealthIndicator {

    private final DownstreamHealth health;

    public DownstreamHealthIndicator(DownstreamHealth health) {
        this.health = health;
    }

    @Override
    public Health health() {
        if (health.isHealthy()) {
            return Health.up().build();
        }
        return Health.down()
            .withDetail("reason", "Simulated outage is active")
            .build();
    }
}
