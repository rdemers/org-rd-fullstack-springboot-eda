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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class RestExceptionAdvice {

    private static final Logger logger = LoggerFactory.getLogger(RestExceptionAdvice.class);

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, WebRequest request) {
        
        // Log the conflict with details for debugging purposes.
        Throwable rootCause = ex.getMostSpecificCause();
        String message = rootCause.getMessage();
        String path = request.getDescription(false).replace("uri=", "");
        logger.warn("Database conflict detected on [{}]: {}.", path, message);

        // Optionally, you can include more details in the response body if needed.
        // Next release: consider adding a more detailed response body for better client feedback.
        //
        //Map<String, Object> body = new HashMap<>();
        //body.put("status", HttpStatus.CONFLICT.value());
        //body.put("error", "Conflict");
        //body.put("message", "message.conflict");
        //body.put("path", path);
        //return new ResponseEntity<>(body, HttpStatus.CONFLICT);
        return new ResponseEntity<>(HttpStatus.CONFLICT); // HTTP 409 Conflict.
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAllUncaughtExceptions(
            Exception ex, WebRequest request) {
        
        String path = request.getDescription(false).replace("uri=", "");
         String message = ex.getMessage();
        logger.error("Uncaught server exception on [{}]: {}.", path, message);

        // Optionally, you can include more details in the response body if needed.
        // Next release: consider adding a more detailed response body for better client feedback.
        //
        //Map<String, Object> body = new HashMap<>();
        //body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        //body.put("error", "Internal Server Error");
        //body.put("message", message);
        //body.put("path", path);
        //return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
        return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR); // HTTP 500 Internal Server Error.
    }
}

// Alternative approach using UnexpectedRollbackException handling, commented out for now.
//
//@RestControllerAdvice
//public class RestExceptionAdvice {
//
//    @ExceptionHandler(DataIntegrityViolationException.class)
//    public ResponseEntity<Void> handleConflict() {
//        return new ResponseEntity<>(HttpStatus.CONFLICT); // HTTP 409 Conflict.
//    }
//}