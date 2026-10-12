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
@RequestMapping("/api/corroboration")
@SecurityRequirement(name = "SecureAPI")
public class CorroborationController {

    private static final Logger logger =
        LoggerFactory.getLogger(CorroborationController.class);

    private final ConciliationSrv corroborationSrv;

    public CorroborationController(ConciliationSrv corroborationSrv) {
        super();
        this.corroborationSrv = corroborationSrv;
    }

    @PreAuthorize("hasRole('ROLE_SELECT')")
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Run the internal corroboration (read-only).", description = "CorroborationSrv.Report")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success; see Report.ok() and the findings."),
        @ApiResponse(responseCode = "500", description = "Exception/Internal error. Call support.")
    })
    public ResponseEntity<List<Finding>> execute() {
        try {
            // 200 even when anomalies are found: they are the report's content, not a request failure.
            return new ResponseEntity<>(corroborationSrv.execute(), HttpStatus.OK);
        } catch (Exception ex) {
            logger.error("Corroboration exception: {}.", ex.getMessage());
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
