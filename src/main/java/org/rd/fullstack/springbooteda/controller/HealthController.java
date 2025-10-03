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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/health")
@SecurityRequirement(name = "SecureAPI")
public class HealthController {

    final static String CST_DOWN  = "The app is now DOWN.";
    final static String CST_UP    = "The app is now UP.";

    @Autowired
    private ApplicationEventPublisher publisher;

    public HealthController() {
        super();
    }

   /**
     * Liveness state of the application.
     *
     * An application is considered active when it is running with a correct internal state. The failure of liveliness
     * means that the internal state of the app is broken and we can't recover from it. Therefore, the infrastructure
     * (EKS) should restart the application.
     *
     * - Example: The application/service has a fatal runtime exception.
     *
     * An application is considered live when it's running with a correct internal state. Liveness failure means that the internal
     * state of the application is broken and we cannot recover from it. As a result, the infrastructurecure (EKS) should restart
     * the application.
     *
     * - Example: Application/service is having a fatal runtime exception.
     */
    @RequestMapping(value = "/liveness_state_down", method = RequestMethod.GET, produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Report that the internal state of the application is broken.", description = "String.class")
    @ApiResponses(value = { 
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "401", description = "Unauthorized."), 
        @ApiResponse(responseCode = "403", description = "Forbidden."),
        @ApiResponse(responseCode = "404", description = "Not found.") 
    })
    public String goLivenessStateDown() {
        AvailabilityChangeEvent.publish(publisher, this, LivenessState.BROKEN);
        return CST_DOWN;
    }

    @RequestMapping(value = "/liveness_state_up", method = RequestMethod.GET, produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Signal that the application's internal state is valid.", description = "String.class")
    @ApiResponses(value = { 
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "401", description = "Unauthorized."), 
        @ApiResponse(responseCode = "403", description = "Forbidden."),
        @ApiResponse(responseCode = "404", description = "Not found.") 
    })
    public String goLivenessStateUp() {
        AvailabilityChangeEvent.publish(publisher, this, LivenessState.CORRECT);
        return CST_UP;
    }

   /**
     * Readiness state of the application.
     *
     * Prepare failure means the application is unable to accept traffic and the infrastructure (EKS)
     * should stop routing requests to it.
     *
     * - Example: Application/services usually perform a graceful shutdown.
     *
     * Readiness failure means that the application is not able to accept traffic and that the infrastructure (EKS) should stop
     * routing requests to it.
     *
     * - Example: application/services is usually going to a graceful shutdown.
     */
    @RequestMapping(value = "/readiness_state_down", method = RequestMethod.GET, produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Report that the application is no longer accepting requests.", description = "String.class")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "401", description = "Unauthorized."), 
        @ApiResponse(responseCode = "403", description = "Forbidden."),
        @ApiResponse(responseCode = "404", description = "Not found.") 
    })
    public String goReadinessStateDown() {
        AvailabilityChangeEvent.publish(publisher, this, ReadinessState.REFUSING_TRAFFIC);
        return CST_DOWN;
    }

    @RequestMapping(value = "/readiness_state_up", method = RequestMethod.GET, produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Signal that the application is accepting requests.", description = "String.class")
    @ApiResponses(value = { 
        @ApiResponse(responseCode = "200", description = "Success|OK."),
        @ApiResponse(responseCode = "401", description = "Unauthorized."), 
        @ApiResponse(responseCode = "403", description = "Forbidden."),
        @ApiResponse(responseCode = "404", description = "Not found.") 
    })
    public String goReadinessStateUp() {
        AvailabilityChangeEvent.publish(publisher, this, ReadinessState.ACCEPTING_TRAFFIC);
        return CST_UP;
    }
}