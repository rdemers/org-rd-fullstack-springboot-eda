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

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.stream.Stream;

@Converter(autoApply = true)
public class ResultConverter implements AttributeConverter<Result, Integer> {
    @Override
    public Integer convertToDatabaseColumn(Result result) {

        if (result == null) 
            return null;
        return result.getValue();
    }

    @Override
    public Result convertToEntityAttribute(Integer value) {
        if (value == null) 
            return null;
        
        return Stream.of(Result.values())
                .filter(r -> r.getValue() == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Result value: " + value));
    }
}