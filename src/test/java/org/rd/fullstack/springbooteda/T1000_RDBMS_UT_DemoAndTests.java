/*
 * Copyright 2023; Réal Demers.
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

package org.rd.fullstack.springbootnuxt;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.rd.fullstack.springbootnuxt.config.Application;
import org.rd.fullstack.springbootnuxt.util.JwtUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.web.WebAppConfiguration;


/*
 * See POM.XML file
 * - Plugins section: maven-surefire-plugin
 * - Unit tests VS integrated tests.
 */
@SpringBootTest(classes = Application.class)
@WebAppConfiguration
// @ActiveProfiles("test") If specific configuration file. Example: application-test.yml
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Tests de l'outil pour la gestion des jetons.")
public class T1000_JwtUtils_UT_Tests {

    private static final Logger logger = LoggerFactory.getLogger(T1000_JwtUtils_UT_Tests.class);

    @Autowired
    private JwtUtils jwtUtils;

    // Encoding according to configuration file settings.
    // - secret: the.beautiful.secret.key.to.change
    // - expiration: 30000
    // - authorities: rd.roles
    private final String jetonJWT = "eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiIzNzJjMDhjMS02OGY5LTQ3YmQtODdhOS1iNjY0YTIxZTZhMWMiLCJzdWIiOiJyb290IiwicmQucm9sZXMiOiJST0xFX1NFTEVDVCxST0xFX0lOU0VSVCxST0xFX1VQREFURSxST0xFX0RFTEVURSIsImlhdCI6MTcxODM4NjQ1NH0.AIS4YFJCUIIv0IzTxdDp3MqQXDyuhQBFSJnJmF6b06c";

    public T1000_JwtUtils_UT_Tests() {
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
    public void jwtUtils_validateJwtToken() throws Exception {
        assertTrue(jwtUtils.validateJwtToken(jetonJWT),"***** The token is invalid.");
        logger.info("***** The token is valid.");
     }

    @Test
    @Order(2)
    public void jwtUtils_getUserNameFromJwtToken() throws Exception {
        assertTrue(jwtUtils.getUserNameFromJwtToken(jetonJWT).compareTo("root") == 0);
        logger.info("***** The username is valid.");
    }
}