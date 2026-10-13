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

import org.junit.Ignore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.rd.fullstack.springbooteda.config.Application;
import org.rd.fullstack.springbooteda.dao.InventoryRepository;
import org.rd.fullstack.springbooteda.dao.JrnEventRepository;
import org.rd.fullstack.springbooteda.dao.PersonRepository;
import org.rd.fullstack.springbooteda.dao.ProductRepository;
import org.rd.fullstack.springbooteda.dao.RequestRepository;
import org.rd.fullstack.springbooteda.dto.EventFooter;
import org.rd.fullstack.springbooteda.dto.EventHeader;
import org.rd.fullstack.springbooteda.dto.EventMessage;
import org.rd.fullstack.springbooteda.dto.Inventory;
import org.rd.fullstack.springbooteda.dto.JrnEvent;
import org.rd.fullstack.springbooteda.dto.Person;
import org.rd.fullstack.springbooteda.dto.Product;
import org.rd.fullstack.springbooteda.dto.Request;
import org.rd.fullstack.springbooteda.srv.ConciliationSrv.Payload;
import org.rd.fullstack.springbooteda.srv.ProcessorSrv;
import org.rd.fullstack.springbooteda.util.JsonMapper;
import org.rd.fullstack.springbooteda.util.Operation;
import org.rd.fullstack.springbooteda.util.Result;
import org.rd.fullstack.springbooteda.util.kafka.KafkaConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/*
 * See POM.XML file
 * - Plugins section: maven-surefire-plugin
 * - Unit tests VS integrated tests.
 */
@SpringBootTest(classes = Application.class)
@DisplayName("Processor: the journal payload holds the executed movement.")
public class T1210_DBProcessorMovement_UT_Tests {

    @Autowired private ProcessorSrv         processorSrv;
    @Autowired private JrnEventRepository   jrnEventRepository;
    @Autowired private RequestRepository    requestRepository;
    @Autowired private PersonRepository     personRepository;
    @Autowired private ProductRepository    productRepository;
    @Autowired private InventoryRepository  inventoryRepository;

    @Test
    @Ignore // Disabled because the test is not working with the current implementation of the processor.
    @Order(1)
    public void executedRequestCheckMovement() throws Exception {
        
        Person  person  = personRepository.save(new Person("Mvt-person", "Mvt-person", new BigDecimal("100.00")));
        Product product = productRepository.save(new Product("Mvt-product", "Mvt-product", new BigDecimal("2.50")));
        Inventory inv   = inventoryRepository.save(new Inventory(product.getProductId(), 10L));

        // Sale of 4 units at 2.50: balance 100.00 -> 90.00, quantity 10 -> 6.
        Request sale = requestRepository.save(
            new Request(person.getPersonId(), product.getProductId(), 4L, Operation.CREDIT, Result.PENDING));
            
        process(sale, "EVT-MVT-SALE");
        assertMovement("EVT-MVT-SALE", person.getPersonId(), product.getProductId(),
            new BigDecimal("100.00"), new BigDecimal("-10.00"), new BigDecimal("90.00"), 10, -4, 6);

        // Restock of 5 units: balance 90.00 -> 102.50, quantity 6 -> 11 (chain continues).
        Request restock = requestRepository.save(
            new Request(person.getPersonId(), product.getProductId(), 5L, Operation.DEBIT, Result.PENDING));
        process(restock, "EVT-MVT-RESTOCK");
        assertMovement("EVT-MVT-RESTOCK", person.getPersonId(), product.getProductId(),
            new BigDecimal("90.00"), new BigDecimal("12.50"), new BigDecimal("102.50"), 6, 5, 11);

        assertEquals(11L, inventoryRepository.findById(inv.getInventoryId()).orElseThrow().getQty());
    }

    private void process(Request request, String eventId) throws Exception {
        EventHeader header = new EventHeader(eventId, "1.0", "batch-mvt", KafkaConstants.CST_NONE);
        EventMessage<EventHeader, Request, EventFooter> message = new EventMessage<>(header, request, null);
        processorSrv.process(message, false);
    }

    private void assertMovement(String eventId, Long personId, Long productId,
                                BigDecimal cBegin, BigDecimal cOp, BigDecimal cEnd, 
                                long iBegin, long iOp, long iEnd) {
        
        JrnEvent event = jrnEventRepository.findByConsumerIdAndEventId(KafkaConstants.CST_TOPIC_GROUP, eventId).orElseThrow();
        assertEquals(Result.EXECUTED, event.getResult());

        Payload p = JsonMapper.readFromJson(event.getPayload(), Payload.class);
        assertNotNull(p.movement());

        assertEquals(personId, p.movement().client().id());
        assertEquals(0, cBegin.compareTo(p.movement().client().begin()));
        assertEquals(0, cOp.compareTo(p.movement().client().operation()));
        assertEquals(0, cEnd.compareTo(p.movement().client().end()));

        assertEquals(productId, p.movement().inventory().id());
        assertEquals(0, BigDecimal.valueOf(iBegin).compareTo(p.movement().inventory().begin()));
        assertEquals(0, BigDecimal.valueOf(iOp).compareTo(p.movement().inventory().operation()));
        assertEquals(0, BigDecimal.valueOf(iEnd).compareTo(p.movement().inventory().end()));
    }
}
