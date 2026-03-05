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

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.rd.fullstack.springbooteda.config.Application;
import org.rd.fullstack.springbooteda.dao.InventoryRepository;
import org.rd.fullstack.springbooteda.dao.ProductRepository;
import org.rd.fullstack.springbooteda.dao.RequestRepository;
import org.rd.fullstack.springbooteda.srv.DataGeneratorSrv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = Application.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("DataGenerator demo and tests.")
public class T1100_DBDataGenerator_UT_Tests {
    private static final Logger logger = 
        LoggerFactory.getLogger(T1100_DBDataGenerator_UT_Tests.class);

    @Autowired
    private DataGeneratorSrv dataGenerator;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository; 

    @Autowired
    private RequestRepository requestRepository;

    public T1100_DBDataGenerator_UT_Tests() {
        super();
    }

    @Test
    @Order(1)
    public void dataGeneratorDemoTest() {
        final int CST_NBR_QTY     = 50;
        final int CST_NBR_INV_QTY = 150;
        final int CST_NBR_REQUEST = 500;

        // Generate some requests.
        dataGenerator.genRandomRequest(CST_NBR_REQUEST, CST_NBR_QTY, true);
        assertEquals(CST_NBR_REQUEST, requestRepository.count());
        logger.info("Request repositiry count: {}.",requestRepository.count());

        // Generate inventory for all products.
        dataGenerator.genRandomInventory(CST_NBR_INV_QTY, true);
        assertEquals(inventoryRepository.count(), productRepository.count());
        logger.info("Inventory repositiry count: {}.",inventoryRepository.count());
        logger.info("Product repositiry count: {}.",productRepository.count());
     }
}