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
package org.rd.fullstack.springbooteda.util;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

@Controller
public class HttpGetHandlerGraphQl {

    private static final Log logger = 
        LogFactory.getLog(HttpGetHandlerGraphQl.class);

    private final WebGraphQlHandler graphQlHandler;
    private final ObjectMapper objectMapper;

    public HttpGetHandlerGraphQl(WebGraphQlHandler graphQlHandler, ObjectMapper objectMapper) {
        this.graphQlHandler = graphQlHandler;
        this.objectMapper = objectMapper;
    }

    @GetMapping(value = "/graphql", produces = { MediaType.APPLICATION_JSON_VALUE, "application/graphql-response+json" })
    @ResponseBody
    public Mono<ResponseEntity<Map<String, Object>>> handleRequest(
            @RequestParam(name = "query") String query,
            @RequestParam(name = "operationName", required = false) String operationName,
            @RequestParam(name = "variables", required = false) String variablesJson,
            @RequestHeader Map<String, String> headers) {
        Locale locale = LocaleContextHolder.getLocale();
        HttpHeaders httpHeaders = new HttpHeaders();
        headers.forEach(httpHeaders::add);

        Map<String, Object> input = Map.of("query", query);

        try {
            // Préparer le corps de la requête GraphQL
            Map<String, Object> requestInput = new java.util.HashMap<>(input);

            if (variablesJson != null && !variablesJson.isBlank()) {
                Map<String, Object> variables = objectMapper.readValue(variablesJson, new TypeReference<>() {});
                requestInput.put("variables", variables);
            }

            if (operationName != null && !operationName.isBlank()) {
                requestInput.put("operationName", operationName);
            }

            WebGraphQlRequest graphQlRequest = new WebGraphQlRequest(
                URI.create("/graphql"),
                httpHeaders,
                null,
                null, 
                requestInput,
                Map.of(), // attributes.
                UUID.randomUUID().toString(),
                locale
            );


            if (logger.isDebugEnabled()) {
                logger.debug("GraphQL Request: " + graphQlRequest);
            }

            return graphQlHandler.handleRequest(graphQlRequest)
                    .map(response -> {
                        if (logger.isDebugEnabled()) {
                            logger.debug("GraphQL Execution complete");
                        }

                        return ResponseEntity
                                .ok()
                                .contentType(MediaType.APPLICATION_JSON)
                                .headers(respHeaders -> respHeaders.putAll(response.getResponseHeaders()))
                                .body(response.toMap());
                    });

        } catch (Exception ex) {
            logger.error("Failed to parse variables JSON: {}.", ex);
            return Mono.just(ResponseEntity.badRequest().body(Map.of(
                    "errors", java.util.List.of(Map.of("message", "Invalid 'variables' JSON"))
            )));
        }
    }
}