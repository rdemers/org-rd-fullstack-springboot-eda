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
 * 
 * References:
 * - https://docs.enterprise.spring.io/spring-kafka/reference/testing.html
 * - https://www.baeldung.com/spring-kafka
 * - https://www.baeldung.com/spring-boot-kafka-testing 
 * 
 */

package org.rd.fullstack.springbooteda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.rd.fullstack.springbooteda.config.Application;
import org.rd.fullstack.springbooteda.util.KafkaSandbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/*
 * See POM.XML file
 * - Plugins section: maven-surefire-plugin
 * - Unit tests VS integrated tests.
 */
@SpringBootTest(classes = Application.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("KAFKA toolbox, producer, consumer demo and tests.")
public class T2000_KAFKA_UT_Tests {

    @Autowired
    private KafkaSandbox kafkaBroker;   

    private static final Logger logger = LoggerFactory.getLogger(T2000_KAFKA_UT_Tests.class);

    public T2000_KAFKA_UT_Tests() {
        super();
    }

    @BeforeAll
    static public void setUpBeforeAll() {
        logger.info("@BeforeAll - Runs once before all test methods of this class.");
    }

    @AfterAll
    static void tearDownAfterAll() {
        logger.info("@BeforeAll - Run once after all test methods of this class.");
    }

    @BeforeEach
    void setUpBeforeEach() {
        logger.info("@BeforeEach - Executes before each test method of this class.");
    }

    @AfterEach
    void tearDownAfterEach() {
        logger.info("@BeforeEach - Executes after each test method of this class.");
    }

    @Test
    @Order(1)
    public void kafkaBrokerStartStopDemoTest() throws Exception {

        assertFalse(kafkaBroker.isStarted());
        kafkaBroker.start();

        assertTrue(kafkaBroker.isStarted());
        kafkaBroker.stop();

        assertFalse(kafkaBroker.isStarted());
     }

    @Test
    @Order(2)
    public void kafkaBrokerPropertiesDemoTest() throws Exception {

        assertFalse(kafkaBroker.isStarted());
        kafkaBroker.start();

        String properties = kafkaBroker.getBrokerProperties();
        assertNotNull(properties);
        logger.info(properties);

        assertTrue(kafkaBroker.isStarted());
        kafkaBroker.stop();

        assertFalse(kafkaBroker.isStarted());
     }

    @Test
    @Order(3)
    public void kafkaBrokerTopicsDemoTest() throws Exception {

        assertFalse(kafkaBroker.isStarted());
        kafkaBroker.start();

        kafkaBroker.addTopics(new NewTopic("topic1", 10, (short)1));
        kafkaBroker.addTopics(new NewTopic("topic2", 10, (short)1),
                              new NewTopic("topic3", 10, (short)1));

        kafkaBroker.addTopic("topic4", 10, (short)1);
        kafkaBroker.addTopic("topic5", 10, (short)1);
        Set<String> topics = kafkaBroker.getTopics();

        assertNotNull(topics);
        assertEquals(5, topics.size());

        logger.info(topics.toString());

        assertTrue(kafkaBroker.isStarted());
        kafkaBroker.stop();

        assertFalse(kafkaBroker.isStarted());
     }
}