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

package org.rd.fullstack.springbooteda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.web.WebAppConfiguration;

/*
 * See POM.XML file
 * - Plugins section: maven-surefire-plugin
 * - Unit tests VS integrated tests.
 */
@SpringBootTest(classes = Application.class)
@WebAppConfiguration
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("KAFKA and producer/consomer demo and tests.")
public class T2000_KAFKA_UT_Tests {

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
    public void personRepositoryDemoTest() throws Exception {

        assertNotNull(this);
        logger.info("null");
        assertEquals(1,1);
     }
}