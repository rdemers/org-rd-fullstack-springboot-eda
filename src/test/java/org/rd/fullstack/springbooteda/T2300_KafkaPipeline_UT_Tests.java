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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.rd.fullstack.springbooteda.config.Application;
import org.rd.fullstack.springbooteda.dao.InventoryRepository;
import org.rd.fullstack.springbooteda.dao.RequestRepository;
import org.rd.fullstack.springbooteda.dto.Inventory;
import org.rd.fullstack.springbooteda.dto.Request;
import org.rd.fullstack.springbooteda.srv.DataGeneratorSrv;
import org.rd.fullstack.springbooteda.srv.SmartLifecycleSrv;
import org.rd.fullstack.springbooteda.util.JsonMapper;
import org.rd.fullstack.springbooteda.util.kafka.KafkaSandbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;

@SpringBootTest(classes = Application.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Pipeline demo and tests.")
public class T2300_KafkaPipeline_UT_Tests {

    private static final Logger logger = 
        LoggerFactory.getLogger(T2300_KafkaPipeline_UT_Tests.class);

    @Autowired
    private KafkaSandbox kafkaSandbox;

    @Autowired
    private SmartLifecycleSrv smartLifecycleService;

    @Autowired
    private DataGeneratorSrv dataGenerator;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private RequestRepository requestRepository;

    private AtomicInteger requestCount;
    private ConcurrentLinkedQueue<Throwable> sendExceptions;

    public T2300_KafkaPipeline_UT_Tests() {
        super();
    }

    @Test
    @Order(1)
    public void kafkaPipelineDemoTest() {
        assertTrue(kafkaSandbox.isStarted());

        final int    CST_NBR_QTY       = 50;
        final int    CST_NBR_INV_QTY   = 150;
        final int    CST_NBR_REQUEST   = 500;
        final String CST_REQUEST_TOPIC = "T2300-topic-1";

        // IPs Address/Port.
        String ports = kafkaSandbox.getBootstrapServers();
        assertNotNull(ports);
        logger.info(ports);

        // Create topics.
        kafkaSandbox.addTopic(CST_REQUEST_TOPIC, 10, (short) 1);

        // Kafka stuff & msgCount/latch (see this::processPipeline).
        KafkaTemplate<String, String> template = kafkaSandbox.getKafkaTemplate(CST_REQUEST_TOPIC);
        kafkaSandbox.setupMessageListener(
            CST_REQUEST_TOPIC,
            this::processPipeline
        );

        // Prepare requests.
        dataGenerator.genRandomRequest(CST_NBR_REQUEST, CST_NBR_QTY, true);
        showRequestState();

        // Prepare inventories.
        dataGenerator.genRandomInventory(CST_NBR_INV_QTY, true);
        showInventoryState();

        // Our meters.
        requestCount = new AtomicInteger(0);
        sendExceptions = new ConcurrentLinkedQueue<>();

        // Send requests.
        sendRequests(template, CST_REQUEST_TOPIC);

        // Wait until all messages are processed or timeout.
        await().atMost(40, TimeUnit.SECONDS).until(() -> requestCount.get() == CST_NBR_REQUEST);

        // Assert no send errors.
        assertTrue(sendExceptions.isEmpty(), "Errors during sending: " + sendExceptions);

        // Assert all requests processed.
        assertEquals(CST_NBR_REQUEST, requestCount.get());

        // Show state requests.
        showRequestState();

        // Show inventories.
        showInventoryState();
    }

    private void sendRequests(KafkaTemplate<String, String> template, String topic) {
        if ((topic == null) || (topic.isEmpty()) || (template == null))
            throw new IllegalArgumentException("Invalid parameters.");

        List<Request> lstRequest = requestRepository.findAll();
        assertNotNull(lstRequest);
        logger.info("Size = {}.", lstRequest.size());

        lstRequest.forEach(request -> {
            try {
                template
                    .send(
                        new ProducerRecord<>(
                            topic,
                            String.valueOf(request.getProductId()),
                            JsonMapper.writeToJson(request)
                        )
                    )
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            sendExceptions.add(ex);
                            logger.error("Error sending message: {}.", ex);
                        } else {
                            logger.info(
                                "Message sent: topic={}, partition={}, offset={}.",
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset()
                            );
                        }
                    });
            } catch (Exception ex) {
                sendExceptions.add(ex);
                logger.error("Error sending message: {}.", ex);
            }
        });
    }

    private void showRequestState() {
        List<Request> lstRequest = requestRepository.findAll();
        assertNotNull(lstRequest);

        lstRequest.forEach(request ->
            logger.info("Request : {}.", request.toString())
        );
    }

    private void showInventoryState() {
        List<Inventory> lstInventory = inventoryRepository.findAll();

        assertNotNull(lstInventory);
        lstInventory.forEach(inventory ->
            logger.info("Inventory : {}.", inventory.toString())
        );
    }

    private void processPipeline(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        if (!smartLifecycleService.isRunning()) 
            return;

        logger.info(
            "PIPELINE OUTPUT - Thread={} - Message received: partition={}, offset={}, value={}.",
            Thread.currentThread().getName(),
            record.partition(),
            record.offset(),
            record.value()
        );

        acknowledgment.acknowledge();
        requestCount.incrementAndGet();
    }
}
