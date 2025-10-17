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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

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
import org.rd.fullstack.springbooteda.dao.ProductRepository;
import org.rd.fullstack.springbooteda.dao.RequestRepository;
import org.rd.fullstack.springbooteda.dao.PersonRepository;
import org.rd.fullstack.springbooteda.dao.InventoryRepository;
import org.rd.fullstack.springbooteda.dto.Inventory;
import org.rd.fullstack.springbooteda.dto.Person;
import org.rd.fullstack.springbooteda.dto.Product;
import org.rd.fullstack.springbooteda.dto.Request;
import org.rd.fullstack.springbooteda.util.Operation;
import org.rd.fullstack.springbooteda.util.Result;
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
@DisplayName("Database and repositories demo and tests.")
public class T1000_RDBMS_UT_Tests {

    private static final Logger logger = LoggerFactory.getLogger(T1000_RDBMS_UT_Tests.class);

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private RequestRepository requestRepository;

    public T1000_RDBMS_UT_Tests() {
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

        Person p1;
        Long personId;
        Optional<Person> p2;

        p1 = personRepository.save(new Person("firstName","LastName"));
        personId = p1.getPersonId();

        assertNotNull(personId);
        logger.info("@Test/Person - save/insert :" + p1.toString());

        p1.setFirstName("firstname2");
        personRepository.save(p1);

        p2 = personRepository.findById(personId);
        assertNotNull(p2);
        assertEquals(p2.get().getFirstName(),"firstname2");
        logger.info("@Test/Person - save/update :" + p2.toString());

        List<Person> lstPerson = personRepository.findAll();

        assertTrue(lstPerson != null);
        lstPerson.forEach(person -> logger.info("@Test/Person - findAll :" + person.toString()));
     }


    @Test
    @Order(2)
    public void productRepositoryDemoTest() throws Exception {

        Product p1;
        Long productId;
        Optional<Product> p2;

        p1 = productRepository.save(new Product("Coke", "Coka-Cola", new BigDecimal(1.00)));
        productId = p1.getProductId();

        assertNotNull(productId);
        logger.info("@Test/Product - save/insert :" + p1.toString());

        p1.setCode("Pepsi");
        p1.setDescription("Pepsi Diet");
        productRepository.save(p1);

        p2 = productRepository.findById(productId);
        assertNotNull(p2);
        assertEquals(p2.get().getCode(),"Pepsi");
        logger.info("@Test/Product - save/update :" + p2.toString());

        List<Product> lstProduct = productRepository.findAll();

        assertTrue(lstProduct != null);
        lstProduct.forEach(product -> logger.info("@Test/Product - findAll :" + product.toString()));
     }

    @Test
    @Order(3)
    public void inventoryRepositoryDemoTest() throws Exception {

        Product p1;
        Inventory i1;
        Long inventoryId;
        Optional<Inventory> i2;

        p1 = productRepository.save(new Product("Sprite", "Sprite", new BigDecimal(1.00)));
        i1 = inventoryRepository.save(new Inventory(p1.getProductId(), 1000L));

        inventoryId = i1.getInventoryId();

        assertNotNull(inventoryId);
        logger.info("@Test/Inventory - save/insert :" + i1.toString());

        i2 = inventoryRepository.findById(inventoryId);
        assertNotNull(i2);
        assertEquals(i2.get().getProductId(),p1.getProductId());
        logger.info("@Test/Inventory - findById :" + i2.toString());

        List<Inventory> lstInventory = inventoryRepository.findAll();

        assertNotNull(lstInventory);
        lstInventory.forEach(inventory -> logger.info("@Test/Inventory - findAll :" + inventory.toString()));
     }

    @Test
    @Order(4)
    public void requestRepositoryDemoTest() throws Exception {

        Person pe1;
        Product po1;
        Request r1;

        Long requestId;
        Optional<Request> r2;

        pe1 = personRepository.save(new Person("Test4","Test4"));
        assertNotNull(pe1);

        po1 = productRepository.save(new Product("Product4", "Product4", new BigDecimal(1.00)));
        assertNotNull(po1);
        
        r1 = requestRepository.save(new Request(pe1.getPersonId(),po1.getProductId(),10l,Operation.PULL.getValue(), Result.PENDING.getValue()));
        assertNotNull(r1);

        requestId = r1.getRequestId();
        assertNotNull(requestId);

        logger.info("@Test/Request - save/insert :" + r1.toString());

        r2 = requestRepository.findById(requestId);
        assertNotNull(r2);
        assertEquals(r2.get().getRequestId(),requestId);

        logger.info("@Test/Request - findById :" + r2.toString());

        List<Request> lstRequest = requestRepository.findAll();
        assertNotNull(lstRequest);

        lstRequest.forEach(request -> logger.info("@Test/Request - findAll :" + request.toString()));
     }    
}