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
package org.rd.fullstack.springbooteda.dto;

import org.rd.fullstack.springbooteda.util.Operation;
import org.rd.fullstack.springbooteda.util.Result;

public record RequestView(
    Long requestId,
    Long personId,
    String personFirstName,
    String personLastName,
    Long productId,
    String productCode,
    String productDescription,
    Long qty,
    Integer intOperation,
    String strOperation,
    Integer intResult,
    String strResult
) {
    public RequestView(
        Long requestId, Long personId, String personFirstName, String personLastName,
        Long productId, String productCode, String productDescription, Long qty,
        Operation operation, Result result
    ) {
        this(
            requestId, personId, personFirstName, personLastName,
            productId, productCode, productDescription, qty,
            operation.getValue(), operation.name(), // Transformation.
            result.getValue(), result.name()        // Transformation.
        );
    }
}
