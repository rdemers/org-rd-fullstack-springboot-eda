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
package org.rd.fullstack.springbooteda.config;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.rd.fullstack.springbooteda.dao.InventoryRepository;
import org.rd.fullstack.springbooteda.dao.JrnEventRepository;
import org.rd.fullstack.springbooteda.dao.PersonRepository;
import org.rd.fullstack.springbooteda.dao.ProductRepository;
import org.rd.fullstack.springbooteda.dao.RequestRepository;
import org.rd.fullstack.springbooteda.dto.Inventory;
import org.rd.fullstack.springbooteda.dto.JrnEvent;
import org.rd.fullstack.springbooteda.dto.Person;
import org.rd.fullstack.springbooteda.dto.Product;
import org.rd.fullstack.springbooteda.dto.Request;
import org.rd.fullstack.springbooteda.util.EventType;
import org.rd.fullstack.springbooteda.util.Operation;
import org.rd.fullstack.springbooteda.util.Result;
import org.rd.fullstack.springbooteda.util.token.EventToken;
import org.rd.fullstack.springbooteda.util.token.EventTokenPayload;
import org.rd.fullstack.springbooteda.util.token.TokenToolskit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
public class DatabaseConfig {

    @Bean
    CommandLineRunner loadData(PersonRepository personRepository,
                               ProductRepository productRepository,
                               InventoryRepository inventoryRepository, 
                               RequestRepository requestRepository,
                               JrnEventRepository jrnEventRepository,
                               TokenToolskit tokenToolskit) {
        return args -> {
            Logger logger = LoggerFactory.getLogger(getClass());
            logger.info("Generating data...");

            // The persons entities.
            logger.info("Persons ...");

            personRepository.save(new Person("John", "Wick", new BigDecimal("8000.00")));
            personRepository.save(new Person("Jack", "Sparrow", new BigDecimal("8000.00")));
            personRepository.save(new Person("Conan", "Barbarian", new BigDecimal("8000.00")));
            personRepository.save(new Person("Suzan", "Storm", new BigDecimal("8000.00")));
            personRepository.save(new Person("Johnny", "Cash", new BigDecimal("8000.00")));
            personRepository.save(new Person("Tony", "Stark", new BigDecimal("8000.00")));
            personRepository.save(new Person("Sophia", "Madria", new BigDecimal("8000.00")));
            personRepository.save(new Person("James", "Bond", new BigDecimal("8000.00")));
            personRepository.save(new Person("Jolene", "Spinoza", new BigDecimal("8000.00")));
            personRepository.save(new Person("Monica", "Spears", new BigDecimal("8000.00")));
            personRepository.flush();

            // The products entities.
            logger.info("Products ...");

            productRepository.save(new Product("Apple", "Mcinstosh Apple",new BigDecimal("9.99")));
            productRepository.save(new Product("Banana", "Banana split",new BigDecimal("2.50")));
            productRepository.save(new Product("Steak", "T-Bone Steak 4pack",new BigDecimal("49.99")));
            productRepository.save(new Product("Oats", "Quaker Quick Oats",new BigDecimal("9.99")));
            productRepository.save(new Product("Soup", "Campbells chicken soup",new BigDecimal("0.99")));
            productRepository.save(new Product("Milk", "Milk 2% - Lactose free",new BigDecimal("4.99")));
            productRepository.save(new Product("Bread", "Multigrain bread",new BigDecimal("5.99")));
            productRepository.save(new Product("Juice", "Grapefruit juice",new BigDecimal("3.99")));
            productRepository.save(new Product("Potato", "Bag of potatoes",new BigDecimal("4.99")));
            productRepository.save(new Product("Fish", "Atlantic Cod fish",new BigDecimal("29.99")));
            productRepository.flush();

            // The inventories entities.
            productRepository.findAll().forEach(product -> 
                inventoryRepository.save(new Inventory(product.getProductId(),100L)));

            // The requests entities.
            List<Person> lstPerson = personRepository.findAll();
            List<Product> lstProduct = productRepository.findAll();
            int countPerson = lstPerson.size(); 
            int countProduct = lstProduct.size();

            // This will generate 50 x 8 = 400 requests.
            int indexPerson; int indexProduct;
            for (int iii = 0; iii < 50; iii++) {
                indexProduct = ThreadLocalRandom.current().nextInt(countProduct);

                // Each product will have 8 requests.
                // This will create a good test data set for the EDA application listerner.
                for (int jjj = 0; jjj < 8; jjj++) {
                    indexPerson = ThreadLocalRandom.current().nextInt(countPerson);
                    long quantity = ThreadLocalRandom.current().nextInt(10)+1;
                    Request req = new Request(lstPerson.get(indexPerson).getPersonId(),
                                    lstProduct.get(indexProduct).getProductId(), 
                                    quantity, Operation.CREDIT, Result.PENDING);
                    requestRepository.save(req);
                }
            }
            requestRepository.flush();

            // --- Dedicated low-stock product to reproduce the inventory race reliably ---
            // Stock is deliberately small and there is exactly one request per DISTINCT
            // person, so the Hazelcast per-personId lock in PipelineSrv.handle() never
            // serializes these requests against each other — only the stale stock check
            // in ProcessorSrv.process() stands between concurrency and oversell. Publish
            // these without a Kafka key and with latency enabled to reproduce the race.
            //
            logger.info("Race-condition test data ...");
            
            Product raceProduct = productRepository.save(
                new Product("RaceTest", "Low-stock item for oversell race testing", new BigDecimal("1.00")));
            productRepository.flush();
            
            inventoryRepository.save(new Inventory(raceProduct.getProductId(), 5L));
            
            for (Person person : lstPerson) {
                requestRepository.save(new Request(person.getPersonId(), raceProduct.getProductId(),
                                                    1L, Operation.CREDIT, Result.PENDING));
            }
            requestRepository.flush();

            // Fakes JrnEvents.
            logger.info("jrn-events ...");

            final byte  ENV_ID     = 1;
            final short SYSTEM_ID  = 2;
            final short SRV_ID     = 3;

            EventToken token = new EventToken();
            for (int iii = 0; iii < 50; iii++) {
                token.setPayload(new EventTokenPayload(ENV_ID, SYSTEM_ID, SRV_ID, iii));
                jrnEventRepository.save(new JrnEvent("consumerId-fake", 
                        tokenToolskit.encode(token, TokenToolskit.Format.TOKEN_AND_MAC), 
                        "batchId-01","payloadHash", EventType.PROCESSING_REQUESTED, 
                        Result.ERROR, Instant.now(), null));
            }                            
            jrnEventRepository.flush();
            logger.info("Generation completed.");
        };
    }
}