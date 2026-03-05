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
package org.rd.fullstack.springbooteda.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.rd.fullstack.springbooteda.dao.ProductRepository;
import org.rd.fullstack.springbooteda.dto.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@CrossOrigin
@RestController
@RequestMapping("/api")
public class ProductController {
    private static final Logger logger = 
        LoggerFactory.getLogger(ProductController.class);

    @Autowired
    private ProductRepository ProductRepository;

    @GetMapping(value = "/products", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the Product list.", description = "Product.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "204", description = "No products."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<List<Product>> getAll(@RequestParam(name="code", required = false) String code) {
        try {
            List<Product> products = new ArrayList<>();

            if (code == null)
                products.addAll(ProductRepository.findAll());
            else
                products.addAll(ProductRepository.findByCodeContaining(code));

            if (products.isEmpty())
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);

            return new ResponseEntity<>(products, HttpStatus.OK);
        } catch (Exception ex) {
            logger.info("Get list exception: {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping(value = "/products/{ProductId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a Product by his or her identifier.", description = "Product.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "404", description = "Unknown Product."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<Product> get(@PathVariable("ProductId") long ProductId) {
        try {
            Optional<Product> Product = ProductRepository.findById(ProductId);
            return Product.map(value ->
                    new ResponseEntity<>(value, HttpStatus.OK)).orElseGet(()
                        -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
        } catch (Exception ex) {
            logger.info("FindById exception: {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping(value = "/products", consumes = MediaType.APPLICATION_JSON_VALUE,
                                     produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Add a new Product.", description = "Product.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Success|Created."),
        @ApiResponse(responseCode = "409", description = "Duplicate Product."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<Product> save(@RequestBody Product newProduct) {
        try {
            Product Product = ProductRepository.saveAndFlush(newProduct);
            Product.setProductId(null); // Reset ID for new Product for a insert.
            return new ResponseEntity<>(Product, HttpStatus.CREATED);
        } catch (Exception ex) {
            logger.info("Save exception: {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping(value = "/products/{ProductId}", consumes = MediaType.APPLICATION_JSON_VALUE,
                                              produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a Product.", description = "Product.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "404", description = "Unknown Product."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<Product> update(@PathVariable("ProductId") long ProductId, @RequestBody Product majProduct) {
        try {
            Optional<Product> Product = ProductRepository.findById(ProductId);
            if (Product.isPresent()) {
                Product.get().setProduct(majProduct);
                ProductRepository.saveAndFlush(Product.get()); 
                return new ResponseEntity<>(ProductRepository.save(Product.get()), HttpStatus.OK);
            } else
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);

        } catch (Exception ex) {
                logger.info("Update exception: {}.", ex);
                return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping(value = "/products/{ProductId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete a Product.", description = "Product.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Deleted completed."),
        @ApiResponse(responseCode = "404", description = "Unknown Product."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<HttpStatus> delete(@PathVariable("ProductId") long ProductId) {
        try {
            Optional<Product> Product = ProductRepository.findById(ProductId);
            if (Product.isEmpty())
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);

            ProductRepository.deleteById(ProductId);
            ProductRepository.flush();
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception ex) {
            logger.info("Delete exception: {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping(value = "/products", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Destroy all products.", description = "Product.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Deleted all products."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<HttpStatus> deleteAll() {
        try {
            ProductRepository.deleteAll();
            ProductRepository.flush();
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception ex) {
            logger.info("Delete all exception: {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}