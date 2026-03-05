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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.apache.kafka.clients.admin.AdminClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.rd.fullstack.springbooteda.config.Application;
import org.rd.fullstack.springbooteda.util.kafka.KafkaDashboard;
import org.rd.fullstack.springbooteda.util.kafka.KafkaSandbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = Application.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("KAFKA toolbox, statistics demo and tests.")
public class T2000_KafkaStats_UT_Tests {
    private static final Logger logger = 
        LoggerFactory.getLogger(T2000_KafkaStats_UT_Tests.class);

    @Autowired
    private KafkaSandbox kafkaSandbox;

    public T2000_KafkaStats_UT_Tests() {
        super();
    }

    @Test
    @Order(1)
    public void kafkaSandboxStatsDemoTest() {
        assertTrue(kafkaSandbox.isStarted());
        kafkaSandbox.getBrokerMetrics().forEach((name, metric) -> {
            logger.info("Metric: {} = {}.", name.name(), metric.metricValue());
        });

        AdminClient adm = kafkaSandbox.getAdminClient();
        adm.listTopics().names().toCompletionStage().thenAccept(names -> {
            logger.info("Topics in Kafka Sandbox: {}.", names);
        });
    }

    @Test
    @Order(2)
    public void kafkaSandboxDashboardServiceDemoTest() {

        assertTrue(kafkaSandbox.isStarted());
        try {
            KafkaDashboard kafkaDashboard = kafkaSandbox.getDashboardData();

            logger.info("Cluster ID: {} | Nodes: {} | Controller: {}.",
                 kafkaDashboard.clusterId(), 
                 kafkaDashboard.brokerCount(), 
                 kafkaDashboard.controllerNode());

            kafkaDashboard.topics().forEach(topic ->
                logger.info("{} | {} | {}.",
                    topic.name(), topic.partitions(), topic.replicationFactor()));

            kafkaDashboard.groupLagSummaries().forEach(g -> {
                String lagAlert = g.totalLag() > 100 ? " [!]" : "";
                logger.info("Group ID: {} | Status: {} | Total Lag: {}.",
                    g.groupId(),
                    g.status(),
                    g.totalLag(),
                    lagAlert);
            });
        } catch (Exception ex) {
            fail(ex);
        }
    }
 }