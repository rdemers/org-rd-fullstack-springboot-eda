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

package org.rd.fullstack.springbooteda.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.rd.fullstack.springbooteda.dao.ProductRepository;
import org.rd.fullstack.springbooteda.dto.Product;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@CrossOrigin
@RestController
@RequestMapping("/api")
public class ProductController {

    public ProductController() {
        super();
    }

    @Autowired
    private ProductRepository bookRepository;

    @SuppressWarnings("null")
    @GetMapping(value = "/products", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the list of products.", description = "Product.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "204", description = "No products."),
        @ApiResponse(responseCode = "401", description = "Authentication/Authorization required."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<List<Product>> getAllBooks(@RequestParam(name="code", required = false) String code) {
        try {
            List<Product> products = new ArrayList<Product>();

            if (code == null)
                bookRepository.findAll().forEach(products::add);
            else
                bookRepository.findByCodeContaining(code).forEach(products::add);

            if (products.isEmpty())
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);

            return new ResponseEntity<>(products, HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping(value = "/products/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a product by its identifier.", description = "Product.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "401", description = "Authentication/Authorization required."),
        @ApiResponse(responseCode = "404", description = "Unknown product."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<Product> getProductById(@PathVariable("id") long id) {
        Optional<Product> product = bookRepository.findById(id);
        if (product.isPresent())
            return new ResponseEntity<>(product.get(), HttpStatus.OK);
        else
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @SuppressWarnings("null")
    @PostMapping(value = "/products", consumes = MediaType.APPLICATION_JSON_VALUE,
                                      produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Insert a product.", description = "Product.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Success|Created."),
        @ApiResponse(responseCode = "401", description = "Authentication/Authorization required."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<Product> createBook(@RequestBody Product newBook) {
        try {
            Product book = bookRepository.save(new Product(newBook.getTitle(), newBook.getDescription()));
            return new ResponseEntity<>(book, HttpStatus.CREATED);
        } catch (Exception e) {
           return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PreAuthorize("hasRole('ROLE_UPDATE')")
    @PutMapping(value = "/books/{id}", consumes = MediaType.APPLICATION_JSON_VALUE,
                                       produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a book via its identifier.", description = "Book.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "401", description = "Authentication/Authorization required."),
        @ApiResponse(responseCode = "404", description = "Unknown book."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<Product> updateBook(@PathVariable("id") long id, @RequestBody Product newBook) {
        Optional<Product> findBook = bookRepository.findById(id);
        if (findBook.isPresent()) {
            Product book = findBook.get();
            book.setTitle(newBook.getTitle());
            book.setDescription(newBook.getDescription());
            return new ResponseEntity<>(bookRepository.save(book), HttpStatus.OK);
        } else
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @PreAuthorize("hasRole('ROLE_DELETE')")
    @DeleteMapping(value = "/books/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Destroy a book using its identifier.", description = "Book.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Destruction done."),
        @ApiResponse(responseCode = "401", description = "Authentication/Authorization required."),
        @ApiResponse(responseCode = "404", description = "Unknown book."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<HttpStatus> deleteBook(@PathVariable("id") long id) {
        Optional<Product> findBook = bookRepository.findById(id);
        if (! findBook.isPresent())
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);

        try {
            bookRepository.deleteById(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception ex) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PreAuthorize("hasRole('ROLE_DELETE')")
    @DeleteMapping(value = "/books", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Destroy all books.", description = "Book.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Destruction of all books made."),
        @ApiResponse(responseCode = "401", description = "Authentication/Authorization required."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<HttpStatus> deleteAllBooks() {
        try {
            bookRepository.deleteAll();
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception ex) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}