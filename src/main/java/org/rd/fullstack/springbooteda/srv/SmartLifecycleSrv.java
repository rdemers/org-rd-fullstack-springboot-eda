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

package org.rd.fullstack.springbooteda.srv;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

@Service
public class SmartLifecycleSrv implements SmartLifecycle {
    private static final Logger logger = 
        LoggerFactory.getLogger(SmartLifecycleSrv.class);

    private final AtomicBoolean running;

    public SmartLifecycleSrv() {
        super();
        running = new AtomicBoolean(false);
    }

    @Override
    public synchronized void start() {
        if (running.get()) 
            return;

        logger.info("Starting SmartLifecycleService.");
        running.set(true);
    }

    @Override
    public synchronized void stop() {
        if (!running.get()) 
            return;

        logger.info("Stopping SmartLifecycleService.");
        running.set(false);
    }

    public boolean isRunning() {
        return running.get();
    }
}