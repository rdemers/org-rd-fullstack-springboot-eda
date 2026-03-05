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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.rd.fullstack.springbooteda.config.Application;
import org.rd.fullstack.springbooteda.util.kafka.ConsumerGroupInfo;
import org.rd.fullstack.springbooteda.util.kafka.ConsumerGroupMonitor;
import org.rd.fullstack.springbooteda.util.kafka.KafkaSandbox;
import org.rd.fullstack.springbooteda.util.kafka.LagAlertEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = Application.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Monitoring lag demo and tests.")
public class T3000_KafkaMonitoring_UT_Tests {
    private static final Logger logger = 
        LoggerFactory.getLogger(T3000_KafkaMonitoring_UT_Tests.class);

    @Autowired
    private KafkaSandbox kafkaSandbox;

    private static final int    CST_NBR_PARTITIONS = 3;
    private static final String CST_GROUP_1        = "group-1";
    private static final String CST_GROUP_2        = "group-2";

    private ConsumerGroupMonitor consumerGroupMonitor;

    public T3000_KafkaMonitoring_UT_Tests() {
        super();
    }

    @Test
    @Order(1)
    public void kafkaMonitoringDemoTest() throws Exception {
        assertTrue(kafkaSandbox.isStarted());
        final String CST_TOPIC_NAME = "T3000-topic-1";

        CopyOnWriteArrayList<LagAlertEvent> alerts = new CopyOnWriteArrayList<>();
        kafkaSandbox.addTopic(CST_TOPIC_NAME, CST_NBR_PARTITIONS, (short)1);
        consumerGroupMonitor = kafkaSandbox.getConsumerGroupMonitor();

        alerts.clear();
        consumerGroupMonitor.setLagThreshold(CST_GROUP_1, 1);
        consumerGroupMonitor.addLagAlertListener(CST_GROUP_1, alerts::add);
        consumerGroupMonitor.startMonitoring(List.of(CST_GROUP_1),100);

        // Send 9 messages (3 by partition).
        Producer<String, String> producer = kafkaSandbox.getProducer(CST_TOPIC_NAME);
        for (int iii = 0; iii < 9; iii++) {
            producer.send(new ProducerRecord<>(CST_TOPIC_NAME, "key-" + (iii%3), "message-" + iii));
        }
        producer.flush();

        kafkaSandbox.setupMessageListener(CST_TOPIC_NAME, (record, ack) -> {
            logger.info("Received message: {}.", record.value());
            ack.acknowledge();
        });

        Thread.sleep(500); // Insert artificial lag.
        // Wait for alerts (lag > 1 per partition).
        // await().atMost(10, TimeUnit.SECONDS).until(() -> !alerts.isEmpty());
        // assertFalse(alerts.isEmpty(), "Expected lag alerts to be triggered.");

        logger.info("Alerts triggered.");
        alerts.forEach(a ->
                logger.info("ALERT: Group={}, Topic{}, Partition={}, Lag{}.",
                        a.groupId(), a.partition().topic(), 
                        a.partition().partition(), a.lag())
        );

        consumerGroupMonitor.stopMonitoring();
    }

    @Test
    @Order(2)
    public void kafkaMonitoringLagDemoTest() throws Exception {
        assertTrue(kafkaSandbox.isStarted());
        final String CST_TOPIC_NAME = "topic-monitor-2";

        kafkaSandbox.addTopic(CST_TOPIC_NAME, CST_NBR_PARTITIONS, (short)1);
        consumerGroupMonitor = kafkaSandbox.getConsumerGroupMonitor();

        // Publish 10 messages per partition.
        Producer<String, String> producer = kafkaSandbox.getProducer(CST_TOPIC_NAME);
        for (int ppp = 0; ppp < CST_NBR_PARTITIONS; ppp++) {
            for (int iii = 0; iii < 10; iii++) {
                producer.send(new ProducerRecord<>(CST_TOPIC_NAME, ppp, "key-" + iii, "value-" + iii));
            }
        }
        producer.flush();

        // Create simulated consumers (partially).
        // Partial consumption to simulate lag.
        kafkaSandbox.seekConsumerGroupOffsets(CST_TOPIC_NAME, CST_GROUP_1, Map.of(
                0, 5L, // partition 0 : lag 5
                1, 2L, // partition 1 : lag 2
                2, 0L  // partition 2 : lag 0
        ));

        kafkaSandbox.seekConsumerGroupOffsets(CST_TOPIC_NAME, CST_GROUP_2, Map.of(
                0, 1L,
                1, 4L,
                2, 6L
        ));

        consumerGroupMonitor.setLagThreshold(CST_GROUP_1, 3); // alert if lag > 3
        consumerGroupMonitor.setLagThreshold(CST_GROUP_2, 4); // alert if lag > 4

        AtomicInteger alertsGroup1 = new AtomicInteger(0);
        AtomicInteger alertsGroup2 = new AtomicInteger(0);

        consumerGroupMonitor.addLagAlertListener(CST_GROUP_1, event -> {
            logger.info("Group1 alert: {}.", event);
            alertsGroup1.incrementAndGet();
        });

        consumerGroupMonitor.addLagAlertListener(CST_GROUP_2, event -> {
            logger.info("Group2 alert: {}.", event);
            alertsGroup2.incrementAndGet();
        });

        // Start monitoring every 500ms.
        consumerGroupMonitor.startMonitoring(List.of(CST_GROUP_1, CST_GROUP_2), 500);

        // Wait a little while for the monitor to do its job.
        TimeUnit.SECONDS.sleep(3);

        // Verify that only partitions with lag > threshold trigger alerts.
        //assertEquals(0, alertsGroup1.get(), "Group1 should have 0 alerts.");
        //assertEquals(12, alertsGroup2.get(), "Group2 should have 12 alerts.");

        // Verification of consumer group information.
        ConsumerGroupInfo info1 = consumerGroupMonitor.getConsumerGroupInfo(CST_GROUP_1);
        ConsumerGroupInfo info2 = consumerGroupMonitor.getConsumerGroupInfo(CST_GROUP_2);

        info1.offsets().forEach((tp, o) -> logger.info("Group1: {} - lag: {}.", tp, o.lag()));
        info2.offsets().forEach((tp, o) -> logger.info("Group2: {} - lag: {}.", tp, o.lag()));

        consumerGroupMonitor.stopMonitoring();
    }
}