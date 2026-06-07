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

import java.util.concurrent.CompletableFuture;

import javax.annotation.Nonnull;

import org.rd.fullstack.springbooteda.dto.PipelineContext;
import org.rd.fullstack.springbooteda.dto.StatsContext;
import org.rd.fullstack.springbooteda.srv.PipelineSrv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
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
@RequestMapping("/api/pipeline")
@SecurityRequirement(name = "SecureAPI")
public class PipelineController {

    private static final Logger logger = 
        LoggerFactory.getLogger(PipelineController.class);

    @Autowired
    private PipelineSrv pipelineSrv;

    private volatile CompletableFuture<PipelineContext> future;

    public PipelineController() {
        super();
        future = null;
    }

    @PreAuthorize("hasRole('ROLE_INSERT')")
    @PostMapping(value = "/start", consumes = MediaType.APPLICATION_JSON_VALUE,
                                   produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Start a pipeline.", description = "PipelineContext.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Success|Created."),
        @ApiResponse(responseCode = "409", description = "Duplicate execute."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<PipelineContext> start(@RequestBody @Nonnull PipelineContext pipelineContext) {
        try {
            future = pipelineSrv.start(pipelineContext);
            return new ResponseEntity<>(pipelineContext, HttpStatus.CREATED);
        } catch (Exception ex) {
            logger.error("Start exception: {}.", ex.getMessage());
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PreAuthorize("hasRole('ROLE_INSERT')")
    @PostMapping(value = "/reset", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Reset the pipeline stats and state.", description = "StatsContext.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Success|Created."),
        @ApiResponse(responseCode = "409", description = "Illegal pipeline state."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<StatsContext> reset() {
        try {
            if ((future != null) && (!future.isDone())) {
                throw new IllegalStateException("Pipeline is currently executing.");
            }

            StatsContext statsContext = pipelineSrv.resetStatsContext();
            return new ResponseEntity<>(statsContext, HttpStatus.CREATED);
        } catch (IllegalStateException ex) {
            logger.error("Reset state exception: {}.", ex);
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        } catch (Exception ex) {
            logger.error("Reset state exception: {}.", ex.getMessage());
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PreAuthorize("hasRole('ROLE_SELECT')")
    @GetMapping(value = "/context", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the pipeline parameters.", description = "PipelineContext.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<PipelineContext> getContext() {
        try {
            PipelineContext pipelineContext = pipelineSrv.getPipelineContext();
            return new ResponseEntity<>(pipelineContext, HttpStatus.OK);
        } catch (Exception ex) {
            logger.error("Get context exception: {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PreAuthorize("hasRole('ROLE_SELECT')")
    @GetMapping(value = "/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the current pipeline stats and state.", description = "StatsContext.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<StatsContext> getStats() {
        try {
            StatsContext statsContext = pipelineSrv.getStatsContext();
            return new ResponseEntity<>(statsContext, HttpStatus.OK);
        } catch (Exception ex) {
            logger.error("Get stats exception: {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PreAuthorize("hasRole('ROLE_UPDATE')")
    @PutMapping(value = "/pause/{value}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Pause or resume the Kafka processor listener.", description = "PipelineContext.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<PipelineContext> pause(@PathVariable("value") boolean value) {
        try {
            // Applied immediately and independently of the run lifecycle: updates the
            // distributed context AND pauses/resumes the Kafka listener.
            PipelineContext pipelineContext = pipelineSrv.setPause(value);
            return new ResponseEntity<>(pipelineContext, HttpStatus.OK);
        } catch (Exception ex) {
            logger.error("Pause exception: {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PreAuthorize("hasRole('ROLE_UPDATE')")
    @PutMapping(value = "/context", consumes = MediaType.APPLICATION_JSON_VALUE,
                                    produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update the pipeline parameters.", description = "PipelineContext.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "409", description = "Illegal pipeline state."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<PipelineContext> setContext(@RequestBody @Nonnull PipelineContext pipelineContext) {
        try {
            if ((future != null) && (!future.isDone())) {
                throw new IllegalStateException("Pipeline is currently executing.");
            }

            this.pipelineSrv.setPipelineContext(pipelineContext);
            return new ResponseEntity<>(pipelineContext, HttpStatus.OK);
        } catch (IllegalStateException ex) {
            logger.error("Set context exception: {}.", ex);
            return new ResponseEntity<>(HttpStatus.CONFLICT);
        } catch (Exception ex) {
            logger.error("Set context exception: {}.", ex);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}