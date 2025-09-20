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

import org.rd.fullstack.springbooteda.dto.Product;
import org.rd.fullstack.springbooteda.dao.ProductRepository;
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
    CommandLineRunner loadData(ProductRepository bookRepository) {
        return args -> {
            Logger logger = LoggerFactory.getLogger(getClass());
            if (bookRepository.count() != 0L) {
                logger.info("??? Data already exists ???");
                return;
            }

            logger.info("Generating dummy data...");

            // The books with a title and a description.
            logger.info("Products ...");
            
            bookRepository.save(new Product("Maverick", "The life and fate of a Navy Pilot."));
            bookRepository.save(new Product("Thor", "The guy looking for his hammer."));
            bookRepository.save(new Product("Harley-Davison", "Guys who created a brand of motorcycles."));
            bookRepository.save(new Product("JohnDoe", "The story of a nobody."));
            bookRepository.save(new Product("Ghost-Rider", "The guy who wants to redeem his soul."));
            bookRepository.save(new Product("Jesus", "The man who became God."));
            bookRepository.save(new Product("Spartan", "The guy who is looking for an ideal."));
            bookRepository.save(new Product("Enterprise", "The adventures of a spaceship."));
            bookRepository.save(new Product("Moon", "A satellite that wanted to be a planet."));

            logger.info("Generation completed.");
        };
    }
}