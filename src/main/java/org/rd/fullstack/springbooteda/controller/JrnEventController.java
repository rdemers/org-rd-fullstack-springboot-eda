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

import org.rd.fullstack.springbooteda.dao.JrnEventRepository;
import org.rd.fullstack.springbooteda.dto.JrnEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.repository.JpaRepository;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@CrossOrigin
@RestController
@RequestMapping("/api")
@SecurityRequirement(name = "SecureAPI")
public class JrnEventController extends AbstractCrudController<JrnEvent, Long> {

    private static final Logger logger =
        LoggerFactory.getLogger(JrnEventController.class);

    private final JrnEventRepository jrnEventRepository;

    JrnEventController(JrnEventRepository jrnEventRepository) {
        this.jrnEventRepository = jrnEventRepository;
    }

    @Override
    protected JpaRepository<JrnEvent, Long> repository() {
        return jrnEventRepository;
    }

    @Override
    protected Logger logger() {
        return logger;
    }

    @PreAuthorize("hasRole('ROLE_SELECT')")
    @GetMapping(value = "/jrn-events", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the JrnEvent list.", description = "JrnEvent.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "204", description = "No jrn events."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<List<JrnEvent>> getAll(@RequestParam(name = "consumerId", required = false) String consumerId,
                                                 @RequestParam(name = "batchId", required = false) String batchId) {
        try {
            List<JrnEvent> jrnEvents = new ArrayList<>();

            // Both filters, when given together, narrow the result to their intersection
            // instead of consumerId silently shadowing batchId.
            if (consumerId != null && batchId != null)
                jrnEvents.addAll(jrnEventRepository.findByConsumerIdAndBatchId(consumerId, batchId));
            else if (consumerId != null)
                jrnEvents.addAll(jrnEventRepository.findByConsumerId(consumerId));
            else if (batchId != null)
                jrnEvents.addAll(jrnEventRepository.findByBatchId(batchId));
            else
                jrnEvents.addAll(jrnEventRepository.findAll());

            if (jrnEvents.isEmpty())
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);

            return new ResponseEntity<>(jrnEvents, HttpStatus.OK);
        } catch (Exception ex) {
            logger.error("Get list exception: {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PreAuthorize("hasRole('ROLE_SELECT')")
    @GetMapping(value = "/jrn-events/{consumerId}/{eventId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get a jrn event by its identifier.", description = "JrnEvent.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "404", description = "Unknown JrnEvent."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<JrnEvent> get(@PathVariable("consumerId") String consumerId,
                                         @PathVariable("eventId") String eventId) {
        try {
            Optional<JrnEvent> jrnEvent = jrnEventRepository.findByConsumerIdAndEventId(consumerId, eventId);
            return jrnEvent.map(value ->
                    new ResponseEntity<>(value, HttpStatus.OK)).orElseGet(()
                        -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
        } catch (Exception ex) {
            logger.error("FindById exception: {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PreAuthorize("hasRole('ROLE_SELECT')")
    @GetMapping(value = "/jrn-events/{consumerId}/pending", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the not yet processed jrn events for a consumer.", description = "JrnEvent.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "204", description = "No pending jrn events."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<List<JrnEvent>> getPending(@PathVariable("consumerId") String consumerId) {
        try {
            List<JrnEvent> jrnEvents = new ArrayList<>(jrnEventRepository.findByConsumerIdAndProcessedAtIsNull(consumerId));

            if (jrnEvents.isEmpty())
                return new ResponseEntity<>(HttpStatus.NO_CONTENT);

            return new ResponseEntity<>(jrnEvents, HttpStatus.OK);
        } catch (Exception ex) {
            logger.error("Get list exception: {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PreAuthorize("hasRole('ROLE_INSERT')")
    @PostMapping(value = "/jrn-events", consumes = MediaType.APPLICATION_JSON_VALUE,
                                        produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Add a new JrnEvent.", description = "JrnEvent.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Success|Created."),
        @ApiResponse(responseCode = "409", description = "Duplicate JrnEvent."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<JrnEvent> save(@RequestBody JrnEvent newJrnEvent) {
        return doSave(newJrnEvent);
    }

    @Transactional
    @PreAuthorize("hasRole('ROLE_UPDATE')")
    @PutMapping(value = "/jrn-events/{jrnEventId}", consumes = MediaType.APPLICATION_JSON_VALUE,
                                                    produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a JrnEvent.", description = "JrnEvent.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "404", description = "Unknown JrnEvent."),
        @ApiResponse(responseCode = "409", description = "Update would violate a constraint."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<JrnEvent> update(@PathVariable("jrnEventId") long jrnEventId,
                                            @RequestBody JrnEvent majJrnEvent) {
        return doUpdate(jrnEventId, majJrnEvent, (target, source) -> target.setJrnEvent(source));
    }

    @Transactional
    @PreAuthorize("hasRole('ROLE_DELETE')")
    @DeleteMapping(value = "/jrn-events/{jrnEventId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Delete a JrnEvent.", description = "JrnEvent.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Deleted completed."),
        @ApiResponse(responseCode = "404", description = "Unknown JrnEvent."),
        @ApiResponse(responseCode = "409", description = "Update would violate a constraint."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<HttpStatus> delete(@PathVariable("jrnEventId") long jrnEventId){
        return doDelete(jrnEventId);
    }

    @PreAuthorize("hasRole('ROLE_DELETE')")
    @DeleteMapping(value = "/jrn-events", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Destroy all jrn events.", description = "JrnEvent.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Deleted all jrn events."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<HttpStatus> deleteAll() {
        return doDeleteAll();
    }
}
