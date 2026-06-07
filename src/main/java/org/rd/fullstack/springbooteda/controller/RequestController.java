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

import org.rd.fullstack.springbooteda.dao.RequestRepository;
import org.rd.fullstack.springbooteda.dto.Request;
import org.rd.fullstack.springbooteda.dto.RequestCount;
import org.rd.fullstack.springbooteda.dto.RequestView;
import org.rd.fullstack.springbooteda.util.Result;
import org.rd.fullstack.springbooteda.util.ResultConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@CrossOrigin
@RestController
@RequestMapping("/api")
@SecurityRequirement(name = "SecureAPI")
public class RequestController {
    private static final Logger logger = 
        LoggerFactory.getLogger(RequestController.class);

    @Autowired
    private RequestRepository requestRepository;

    @PreAuthorize("hasRole('ROLE_SELECT')")
    @GetMapping(value = "/requests", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the requests list.", description = "Request.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "204", description = "No requests."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<List<Request>> getAll() {
        try {
            List<Request> requests = new ArrayList<>();
            requests.addAll(requestRepository.findAll());
            
            if (requests.isEmpty())
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);

            return new ResponseEntity<>(requests, HttpStatus.OK);
        } catch (Exception ex) {
            logger.error("Get list exception: {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PreAuthorize("hasRole('ROLE_SELECT')")
    @GetMapping(value = "/requests/{requestId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a request by its identifier.", description = "Request.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "404", description = "Unknown request."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<Request> get(@PathVariable("requestId") long requestId) {
        try {
            Optional<Request> request = requestRepository.findById(requestId);
            return request.map(value ->
                    new ResponseEntity<>(value, HttpStatus.OK)).orElseGet(()
                        -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
        } catch (Exception ex) {
            logger.error("FindById exception: {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PreAuthorize("hasRole('ROLE_INSERT')")
    @PostMapping(value = "/requests", consumes = MediaType.APPLICATION_JSON_VALUE,
                                         produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Add a new request.", description = "Request.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Success|Created."),
        @ApiResponse(responseCode = "409", description = "Duplicate request."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<Request> save(@RequestBody Request newRequest) {
        try {
            Request request = requestRepository.saveAndFlush(newRequest);
            return new ResponseEntity<>(request, HttpStatus.CREATED);
        } catch (DataIntegrityViolationException ex) {
            return new ResponseEntity<>(HttpStatus.CONFLICT); // 409
        } catch (Exception ex) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    @PreAuthorize("hasRole('ROLE_UPDATE')")
    @PutMapping(value = "/requests/{requestId}", consumes = MediaType.APPLICATION_JSON_VALUE,
                                                 produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a request.", description = "Request.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "404", description = "Unknown request."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<Request> update(@PathVariable("requestId") long requestId, @RequestBody Request majRequest) {
        try {
            Optional<Request> request = requestRepository.findById(requestId);
            if (request.isPresent()) {
                request.get().setRequest(majRequest);
                return new ResponseEntity<>(requestRepository.saveAndFlush(request.get()), HttpStatus.OK);
            } else
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);

        } catch (Exception ex) {
            logger.error("Update exception: {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    @PreAuthorize("hasRole('ROLE_DELETE')")
    @DeleteMapping(value = "/requests/{requestId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete a request.", description = "Request.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Deleted completed."),
        @ApiResponse(responseCode = "404", description = "Unknown request."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<HttpStatus> delete(@PathVariable("requestId") long requestId) {
        try {
            Optional<Request> request = requestRepository.findById(requestId);
            if (request.isEmpty())
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);

            requestRepository.deleteById(requestId);
            requestRepository.flush();
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception ex) {
            logger.error("Delete exception: {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PreAuthorize("hasRole('ROLE_DELETE')")
    @DeleteMapping(value = "/requests", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Destroy all requests.", description = "Request.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Deleted all requests."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<HttpStatus> deleteAll() {
        try {
            requestRepository.deleteAll();
            requestRepository.flush();
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception ex) {
            logger.error("Delete all exception: {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PreAuthorize("hasRole('ROLE_SELECT')")
    @GetMapping(value = "/requests/view/{requestId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a request by its identifier.", description = "Request.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "404", description = "Unknown request."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<RequestView> getView(@PathVariable("requestId") long requestId) {
        try {
            Optional<RequestView> request = requestRepository.findByRequestIdView(requestId);
            return request.map(value ->
                    new ResponseEntity<>(value, HttpStatus.OK)).orElseGet(()
                        -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
        } catch (Exception ex) {
            logger.error("FindById exception : {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PreAuthorize("hasRole('ROLE_SELECT')")
    @GetMapping(value = "/requests/view", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the requests list with the product details.", description = "RequestView.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "204", description = "No requests."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<List<RequestView>> getAllView() {
        try {
            List<RequestView> requestsViews = new ArrayList<>();
            requestsViews.addAll(requestRepository.findAllView());

            if (requestsViews.isEmpty())
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);

            return new ResponseEntity<>(requestsViews, HttpStatus.OK);
        } catch (Exception ex) {
            logger.error("Get list exception : {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PreAuthorize("hasRole('ROLE_SELECT')")
    @GetMapping(value = "/requests/count", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the requests count by result status.", description = "RequestCount.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "204", description = "No requests."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<RequestCount> getCount() {
        try {
            RequestCount RequestCount = requestRepository.countRequest();
            return new ResponseEntity<>(RequestCount, HttpStatus.OK);
        } catch (Exception ex) {
            logger.error("Get list exception : {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    @PreAuthorize("hasRole('ROLE_UPDATE')")
    @PostMapping(value = "/requests/reset/{result}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Reset every request to the given result status.", description = "RequestView.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "204", description = "No requests."),
        @ApiResponse(responseCode = "400", description = "Invalid result status."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<List<RequestView>> reset(@PathVariable("result") int result) {
        try {
            Result target;
            try {
                target = new ResultConverter().convertToEntityAttribute(result);
            } catch (IllegalArgumentException ex) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }

            requestRepository.resetAllResults(target);
            requestRepository.flush();

            List<RequestView> requestsViews = new ArrayList<>(requestRepository.findAllView());

            if (requestsViews.isEmpty())
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);

            return new ResponseEntity<>(requestsViews, HttpStatus.OK);
        } catch (Exception ex) {
            logger.error("Reset exception : {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}