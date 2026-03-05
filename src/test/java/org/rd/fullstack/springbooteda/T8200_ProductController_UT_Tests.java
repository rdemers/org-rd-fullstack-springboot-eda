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
import org.rd.fullstack.springbooteda.dto.Product;
import org.rd.fullstack.springbooteda.util.JsonMapper;
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
@DisplayName("Controller tests for Product entities.")
public class T8200_ProductController_UT_Tests extends AbstractMVC {

    private final String CST_URI_PRODUCTS = "/api/products";

    public T8200_ProductController_UT_Tests() {
        super();
        mvcInstance = null;
    }

    @Test
    @Order(1)
    public void postProduct() throws Exception {
        MockMvc mvcInstance  = getMvcInstance();
        assertNotNull(mvcInstance);

        Product Product = new Product("Code-01", "Description-01", new BigDecimal("1000.00"));
        String inputJson = JsonMapper.writeToJson(Product);
        assertNotNull(inputJson);

        MvcResult mvcResult = mvcInstance.perform(MockMvcRequestBuilders.post(CST_URI_PRODUCTS)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .content(inputJson))
            .andReturn();

        int status = mvcResult.getResponse().getStatus();
        assertEquals(201, status); // Created.

        String content = mvcResult.getResponse().getContentAsString();
        assertNotNull(content);

        Product = JsonMapper.readFromJson(content, Product.class);
        assertNotNull(Product,"postProduct(), Product is NULL.");
        logger.info("The post response message : {}.", Product);
    }

    @Test
    @Order(2)
    public void getProducts() throws Exception {
        MockMvc mvcInstance  = getMvcInstance();
        assertNotNull(mvcInstance);

        MvcResult mvcResult = mvcInstance.perform(MockMvcRequestBuilders.get(CST_URI_PRODUCTS)
                .accept(MediaType.APPLICATION_JSON_VALUE))
                .andReturn();

        int status = mvcResult.getResponse().getStatus();
        assertEquals(200, status);

        String content = mvcResult.getResponse().getContentAsString();
        assertNotNull(content);

        List<Product> Products = JsonMapper.readFromJson(content, new TypeReference<>() {});
        Products.forEach(Product -> logger.info("The get response message : {}.", Product.toString()));
    }
}