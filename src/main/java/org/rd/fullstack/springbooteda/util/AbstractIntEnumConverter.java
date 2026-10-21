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

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.persistence.AttributeConverter;

/**
 * Shared JPA {@link AttributeConverter} logic for enums that persist as their
 * {@link IntCodedEnum#getValue()} int code (EventType, Operation, Result, PipelineState).
 * The value-to-enum lookup is built once per converter instance and reused, instead of a
 * linear scan of {@code values()} on every single conversion.
 */
public abstract class AbstractIntEnumConverter<E extends Enum<E> & IntCodedEnum> implements AttributeConverter<E, Integer> {

    private final Class<E> type;
    private final Map<Integer, E> byValue;

    protected AbstractIntEnumConverter(Class<E> type, E[] values) {
        this.type    = type;
        this.byValue = Arrays.stream(values).collect(Collectors.toMap(IntCodedEnum::getValue, e -> e));
    }

    @Override
    public Integer convertToDatabaseColumn(E value) {
        return (value == null) ? null : value.getValue();
    }

    @Override
    public E convertToEntityAttribute(Integer value) {
        if (value == null)
            return null;

        E result = byValue.get(value);
        if (result == null)
            throw new IllegalArgumentException("Unknown " + type.getSimpleName() + " value: " + value);

        return result;
    }
}
