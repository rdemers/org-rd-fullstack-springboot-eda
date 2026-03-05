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
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

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
import org.rd.fullstack.springbooteda.dto.RequestView;
import org.rd.fullstack.springbooteda.dto.RequestViewMapping;
import org.rd.fullstack.springbooteda.util.Operation;
import org.rd.fullstack.springbooteda.util.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(classes = Application.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Database and repositories demo and tests.")
public class T1000_DBJPA_UT_Tests {
    private static final Logger logger = 
        LoggerFactory.getLogger(T1000_DBJPA_UT_Tests.class);

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private RequestRepository requestRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    public T1000_DBJPA_UT_Tests() {
        super();
    }

    @Test
    @Order(1)
    public void personRepositoryDemoTest() {
        Person p1;
        Long personId;
        Optional<Person> p2;

        p1 = personRepository.saveAndFlush(new Person("firstName","LastName", new BigDecimal("1000.00")));
        personId = p1.getPersonId();

        assertNotNull(personId);
        logger.info("Person - Save/insert :{}.", p1);

        p1.setFirstName("firstname2");
        personRepository.saveAndFlush(p1);

        p2 = personRepository.findById(personId);
        assertNotNull(p2);
        assertEquals("firstname2", p2.get().getFirstName());
        logger.info("Person - Save/update :{}.", p2);

        List<Person> lstPerson = personRepository.findAll();

        assertNotNull(lstPerson);
        lstPerson.forEach(person -> logger.info("Person - FindAll :{}.", person.toString()));
     }

    @Test
    @Order(2)
    public void productRepositoryDemoTest() {
        Product p1;
        Long productId;
        Optional<Product> p2;

        p1 = productRepository.saveAndFlush(new Product("Coke", "Coka-Cola", new BigDecimal("1.00")));
        productId = p1.getProductId();

        assertNotNull(productId);
        logger.info("Product - Save/insert:{}.", p1);

        p1.setCode("Pepsi");
        p1.setDescription("Diet Pepsi");
        productRepository.saveAndFlush(p1);

        p2 = productRepository.findById(productId);
        assertNotNull(p2);
        assertEquals("Pepsi", p2.get().getCode());
        logger.info("Product - Save/update :{}.", p2);

        List<Product> lstProduct = productRepository.findAll();

        assertNotNull(lstProduct);
        lstProduct.forEach(product -> logger.info("Product - FindAll: {}.", product.toString()));
     }

    @Test
    @Order(3)
    public void inventoryRepositoryDemoTest() {
        int nbRow;
        Product p1;
        Inventory i1;
        Long inventoryId;
        Optional<Inventory> i2;

        p1 = productRepository.saveAndFlush(new Product("Sprite", "Sprite", new BigDecimal("1.00")));
        i1 = inventoryRepository.saveAndFlush(new Inventory(p1.getProductId(), 1000L));

        inventoryId = i1.getInventoryId();

        assertNotNull(inventoryId);
        logger.info("Inventory - Save/insert: {}.", i1);

        i2 = inventoryRepository.findById(inventoryId);
        assertNotNull(i2);
        assertEquals(i2.get().getProductId(),p1.getProductId());
        logger.info("Inventory - FindById: {}.", i2);

        // Method 1.
        transactionTemplate.execute(status -> {
            assertEquals(1, inventoryRepository.creditQTY(10L, i1.getInventoryId()));
            return null;
        });

        // Method 2.
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setName("MyTestManualTransaction");
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        TransactionStatus status = transactionManager.getTransaction(def);
        try {
            nbRow = inventoryRepository.debitQTY(10L, i1.getInventoryId());
            assertEquals(1, nbRow);
            transactionManager.commit(status);
        } catch (Exception ex) {
            transactionManager.rollback(status);
            fail(ex);
        }

        List<Inventory> lstInventory = inventoryRepository.findAll();
        assertNotNull(lstInventory);
        lstInventory.forEach(inventory -> logger.info("Inventory - FindAll: {}.", inventory.toString()));
     }

    @Test
    @Order(4)
    public void requestRepositoryDemoTest() {
        Person pe1;
        Product po1;
        Request r1;

        Long requestId;
        Optional<Request> r2;

        pe1 = personRepository.saveAndFlush(new Person("Test4","Test4", new BigDecimal("1000.00")));
        assertNotNull(pe1);

        po1 = productRepository.saveAndFlush(new Product("Product4", "Product4", new BigDecimal("1.00")));
        assertNotNull(po1);
        
        r1 = requestRepository.saveAndFlush(new Request(pe1.getPersonId(),po1.getProductId(), 10L,Operation.CREDIT, Result.PENDING));
        assertNotNull(r1);

        requestId = r1.getRequestId();
        assertNotNull(requestId);

        logger.info("Request - Save/insert: {}.", r1);

        r2 = requestRepository.findById(requestId);
        assertNotNull(r2);
        assertEquals(r2.get().getRequestId(),requestId);

        logger.info("Request - FindById: {}.", r2);

        List<Request> lstRequest = requestRepository.findAll();
        assertNotNull(lstRequest);

        lstRequest.forEach(request -> logger.info("Request - FindAll: {}.", request.toString()));
     }

    @Test
    @Order(5)
    public void requestViewRepositoryDemoTest() {
        List<RequestView> lstRequestView = requestRepository.findAllView();
        assertNotNull(lstRequestView);
        lstRequestView.forEach(request -> logger.info("Request - FindAll: {}.", request.toString()));

        List<RequestViewMapping> lstRequestViewMapping = requestRepository.findAllViewNative();
        assertNotNull(lstRequestViewMapping);

        List<RequestView> records =
            lstRequestViewMapping.stream().map(p -> new RequestView(
                p.getRequestId(),
                p.getPersonId(),
                p.getPersonFirstName(),
                p.getPersonLastName(),
                p.getProductId(),
                p.getProductCode(),
                p.getProductDescription(),
                p.getQty(),
                p.getIntOperation(),
                p.getStrOperation(),
                p.getIntResult(),
                p.getStrResult())).toList();

        assertNotNull(records);
        records.forEach(request -> logger.info("Request - FindAllNative: {}.", request.toString()));
     }
}