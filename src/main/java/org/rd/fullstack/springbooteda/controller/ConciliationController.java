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

import java.util.List;

import org.rd.fullstack.springbooteda.srv.ConciliationSrv;
import org.rd.fullstack.springbooteda.srv.ConciliationSrv.Finding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
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
public class ConciliationController {

    private static final Logger logger =
        LoggerFactory.getLogger(ConciliationController.class);

    private final ConciliationSrv conciliationSrv;

    public ConciliationController(ConciliationSrv conciliationSrv) {
        super();
        this.conciliationSrv = conciliationSrv;
    }

    @PreAuthorize("hasRole('ROLE_SELECT')")
    @GetMapping(value = "/conciliation/findings", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Run the internal conciliation (read-only).", description = "ConciliationSrv.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success; see findings."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<List<Finding>> execute() {
        try {
            // 200 even when anomalies are found: they are the report's content, not a request failure.
            return new ResponseEntity<>(conciliationSrv.execute(), HttpStatus.OK);
        } catch (Exception ex) {
            logger.error("Conciliation exception: {}.", ex.getMessage());
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
