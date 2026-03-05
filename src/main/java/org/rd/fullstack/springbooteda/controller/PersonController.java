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

import org.rd.fullstack.springbooteda.dao.PersonRepository;
import org.rd.fullstack.springbooteda.dto.Person;
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
public class PersonController {
    private static final Logger logger = 
        LoggerFactory.getLogger(PersonController.class);

    @Autowired
    private PersonRepository personRepository;

    @GetMapping(value = "/persons", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the person list.", description = "Person.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "204", description = "No persons."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<List<Person>> getAll(@RequestParam(name="name", required = false) String name) {
        try {
            List<Person> persons = new ArrayList<>();

            if (name == null)
                persons.addAll(personRepository.findAll());
            else
                persons.addAll(personRepository.findByFirstNameContaining(name));

            if (persons.isEmpty())
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);

            return new ResponseEntity<>(persons, HttpStatus.OK);
        } catch (Exception ex) {
            logger.info("Get list exception: {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping(value = "/persons/{personId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a person by his or her identifier.", description = "Person.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "404", description = "Unknown person."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<Person> get(@PathVariable("personId") long personId) {
        try {
            Optional<Person> person = personRepository.findById(personId);
            return person.map(value ->
                    new ResponseEntity<>(value, HttpStatus.OK)).orElseGet(()
                        -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
        } catch (Exception ex) {
            logger.info("FindById exception: {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping(value = "/persons", consumes = MediaType.APPLICATION_JSON_VALUE,
                                     produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Add a new person.", description = "Person.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Success|Created."),
        @ApiResponse(responseCode = "409", description = "Duplicate person."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<Person> save(@RequestBody Person newPerson) {
        try {
            Person person = personRepository.saveAndFlush(newPerson);
            person.setPersonId(null); // Reset ID for new person for a insert.
            return new ResponseEntity<>(person, HttpStatus.CREATED);
        } catch (Exception ex) {
            logger.info("Save exception: {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping(value = "/persons/{personId}", consumes = MediaType.APPLICATION_JSON_VALUE,
                                              produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a person.", description = "Person.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "404", description = "Unknown person."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<Person> update(@PathVariable("personId") long personId, @RequestBody Person majPerson) {
        try {
            Optional<Person> person = personRepository.findById(personId);
            if (person.isPresent()) {
                person.get().setPerson(majPerson);
                personRepository.saveAndFlush(person.get()); 
                return new ResponseEntity<>(personRepository.save(person.get()), HttpStatus.OK);
            } else
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);

        } catch (Exception ex) {
                logger.info("Update exception: {}.", ex);
                return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping(value = "/persons/{personId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete a person.", description = "Person.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Deleted completed."),
        @ApiResponse(responseCode = "404", description = "Unknown person."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<HttpStatus> delete(@PathVariable("personId") long personId) {
        try {
            Optional<Person> person = personRepository.findById(personId);
            if (person.isEmpty())
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);

            personRepository.deleteById(personId);
            personRepository.flush();
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception ex) {
            logger.info("Delete exception: {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @DeleteMapping(value = "/persons", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Destroy all persons.", description = "Person.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Deleted all persons."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<HttpStatus> deleteAll() {
        try {
            personRepository.deleteAll();
            personRepository.flush();
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception ex) {
            logger.info("Delete all exception: {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}