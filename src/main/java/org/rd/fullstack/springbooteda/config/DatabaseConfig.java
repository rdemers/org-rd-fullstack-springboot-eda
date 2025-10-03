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
package org.rd.fullstack.springbooteda.config;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.rd.fullstack.springbooteda.dao.InventoryRepository;
import org.rd.fullstack.springbooteda.dao.PersonRepository;
import org.rd.fullstack.springbooteda.dao.ProductRepository;
import org.rd.fullstack.springbooteda.dao.RequestRepository;
import org.rd.fullstack.springbooteda.dto.Inventory;
import org.rd.fullstack.springbooteda.dto.Person;
import org.rd.fullstack.springbooteda.dto.Product;
import org.rd.fullstack.springbooteda.dto.Request;
import org.rd.fullstack.springbooteda.util.Operation;
import org.rd.fullstack.springbooteda.util.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatabaseConfig {

    public DatabaseConfig() {
        super();
    }

    @Bean
    CommandLineRunner loadData(PersonRepository personRepository,
                               ProductRepository productRepository,
                               InventoryRepository inventoryRepository, 
                               RequestRepository requestRepository) {
        return args -> {
            Logger logger = LoggerFactory.getLogger(getClass());
            logger.info("Generating data...");

            // The persons entities.
            logger.info("Persons ...");

            personRepository.save(new Person("John", "Wick"));
            personRepository.save(new Person("Jack", "Sparrow"));
            personRepository.save(new Person("Conan", "Barbarian"));
            personRepository.save(new Person("Suzan", "Storm"));
            personRepository.save(new Person("Johnny", "Cash"));
            personRepository.save(new Person("Tony", "Stark"));
            personRepository.save(new Person("Sophia", "Madria"));
            personRepository.save(new Person("James", "Bond"));
            personRepository.save(new Person("Franceska", "Spinoza"));
            personRepository.save(new Person("Monica", "Spears"));

            // The products entities.
            logger.info("Products ...");

            productRepository.save(new Product("Apple", "Mcinstosh Apple",new BigDecimal(9.99)));
            productRepository.save(new Product("Banana", "Banana split",new BigDecimal(4.99)));
            productRepository.save(new Product("Steak", "T-Bone Steak 4pack",new BigDecimal(49.99)));
            productRepository.save(new Product("Oats", "Quaker Quick Oats",new BigDecimal(9.99)));
            productRepository.save(new Product("Soup", "Campbells chicken soup",new BigDecimal(0.99)));
            productRepository.save(new Product("Milk", "Milk 2% - Lactose free",new BigDecimal(4.99)));
            productRepository.save(new Product("Bread", "Multigrain bread",new BigDecimal(5.99)));
            productRepository.save(new Product("Juice", "Grapefruit juice",new BigDecimal(3.99)));
            productRepository.save(new Product("Patato", "Bag of patatos",new BigDecimal(4.99)));
            productRepository.save(new Product("Fish", "Atlantic Cod fish",new BigDecimal(29.99)));

            // The inventories entities.
            productRepository.findAll().forEach(product -> 
                inventoryRepository.save(new Inventory(product.getProductId(),1000L)));

            // The requests entities.
            List<Person> lstPerson = personRepository.findAll();
            List<Product> lstProduct = productRepository.findAll();
            int countPerson = lstPerson.size(); 
            int countProduct = lstProduct.size();

            int indexPerson; int indexProduct;
            for (int iii = 0; iii < 100; iii++) {
                indexPerson = ThreadLocalRandom.current().nextInt(countPerson);
                indexProduct = ThreadLocalRandom.current().nextInt(countProduct);

                // OPERATION : 10-PULL, 20-PUSH, 30-REFILL, 40-REGENERATE.
                // RESULT    : 10-PENDING, 20-BACK-ORDER, 30-EXECUTED,  99-ERROR.
                Request req = new Request(lstPerson.get(indexPerson).getPersonId(),
                                  lstProduct.get(indexProduct).getProductId(), 
                                  10L, Operation.PULL.getValue(), Result.PENDING.getValue());

                requestRepository.save(req);
            }

            logger.info("Generation completed.");
        };
    }
}