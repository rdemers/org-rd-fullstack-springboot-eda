/*
 * Copyright 2025; Réal Demers.
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

package org.rd.fullstack.springbooteda.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Configuration
@EntityScan({"org.rd.fullstack.springbooteda.dto"})
@ComponentScan({"org.rd.fullstack.springbooteda.controller"})
@EnableJpaRepositories({"org.rd.fullstack.springbooteda.dao"})
public class ApplicationConfig {

    @Autowired
    EmbeddedKafkaKraftBroker embeddedKafkaBroker;

    public ApplicationConfig() {
        super();
    }

    @Component
    public class ShutdownEventListener implements ApplicationListener<ContextClosedEvent> {
        @Override
        public void onApplicationEvent(@NonNull ContextClosedEvent event) {

            Logger logger = LoggerFactory.getLogger(getClass());
            logger.info("Kafka cluster - Shutdown initiated...");
            embeddedKafkaBroker.destroy();
            System.clearProperty("spring.kafka.bootstrap-servers");
            logger.info("Kafka cluster - Shutdown completed.");
        }
    }
}