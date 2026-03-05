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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.rd.fullstack.springbooteda.config.Application;
import org.rd.fullstack.springbooteda.dao.ProductRepository;
import org.rd.fullstack.springbooteda.dto.Inventory;
import org.rd.fullstack.springbooteda.dto.InventoryView;
import org.rd.fullstack.springbooteda.dto.Product;
import org.rd.fullstack.springbooteda.util.JsonMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.core.type.TypeReference;

/*
 * See POM.XML file
 * - Plugins section: maven-surefire-plugin
 * - Unit tests VS integrated tests.
 */
@WebAppConfiguration
@SpringBootTest(classes = Application.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Controller tests for Inventory entities.")
public class T8300_InventoryController_UT_Tests extends AbstractMVC {

    @Autowired
    private ProductRepository productRepository;

    private final String CST_URI_INVENTORIES = "/api/inventories";

    public T8300_InventoryController_UT_Tests() {
        super();
        mvcInstance = null;
    }

    @Test
    @Order(1)
    public void postInventory() throws Exception {
        MockMvc mvcInstance  = getMvcInstance();
        assertNotNull(mvcInstance);
        assertNotNull(productRepository);

        Product product = new Product("Test-01 Code", "Test-01 Description", BigDecimal.valueOf(9.99));
        productRepository.save(product);
        productRepository.flush();

        Long productId = product.getProductId();
        Long qty = 10L;

        Inventory inventory = new Inventory(productId, qty);
        String inputJson = JsonMapper.writeToJson(inventory);
        assertNotNull(inputJson);

        MvcResult mvcResult = mvcInstance.perform(MockMvcRequestBuilders.post(CST_URI_INVENTORIES)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .content(inputJson))
            .andReturn();

        int status = mvcResult.getResponse().getStatus();
        assertEquals(201, status); // Created.

        String content = mvcResult.getResponse().getContentAsString();
        assertNotNull(content);

        inventory = JsonMapper.readFromJson(content, Inventory.class);
        assertNotNull(inventory,"postInventory(), Inventory is NULL.");
        logger.info("The post response message: {}.", inventory);
    }

    @Test
    @Order(2)
    public void getInventories() throws Exception {
        MockMvc mvcInstance  = getMvcInstance();
        assertNotNull(mvcInstance);

        MvcResult mvcResult = mvcInstance.perform(MockMvcRequestBuilders.get(CST_URI_INVENTORIES)
                .accept(MediaType.APPLICATION_JSON_VALUE))
                .andReturn();

        int status = mvcResult.getResponse().getStatus();
        assertEquals(200, status);

        String content = mvcResult.getResponse().getContentAsString();
        assertNotNull(content);

        List<Inventory> inventories = JsonMapper.readFromJson(content, new TypeReference<>() {});
        inventories.forEach(inventory -> logger.info("The get response message: {}.", inventory.toString()));
    }

    @Test
    @Order(3)
    public void getInventoriesView() throws Exception {
        MockMvc mvcInstance  = getMvcInstance();
        assertNotNull(mvcInstance);

        MvcResult mvcResult = mvcInstance.perform(MockMvcRequestBuilders.get(CST_URI_INVENTORIES + "/view")
                .accept(MediaType.APPLICATION_JSON_VALUE))
                .andReturn();

        int status = mvcResult.getResponse().getStatus();
        assertEquals(200, status);

        String content = mvcResult.getResponse().getContentAsString();
        assertNotNull(content);

        List<InventoryView> inventoriesView = JsonMapper.readFromJson(content, new TypeReference<>() {});
        inventoriesView.forEach(inventory -> logger.info("The get response message: {}.", inventory.toString()));
    }
}