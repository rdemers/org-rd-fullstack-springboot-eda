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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class JsonMapper {
	private JsonMapper() {} // Static usage only.

	private static final ObjectMapper objectMapper = new ObjectMapper();

	static {
		objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
		objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
		objectMapper.configure(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS, false);
		objectMapper.findAndRegisterModules();
	}

	public static <T> T readFromJson(String json, Class<T> clazz) throws MappingException {
		try {
			return objectMapper.readValue(json, clazz);  // Thread safe ?
		} catch (Exception ex) {
			throw new MappingException(ex);
		}
	}

	public static String writeToJson(Object obj) throws MappingException {
		try {
			return objectMapper.writeValueAsString(obj); // Thread safe ?
		} catch (Exception ex) {
			throw new MappingException(ex);
		}
	}

	public static <T> T readFromJson(String json, TypeReference<T> typeReference) {
		try {
			return objectMapper.readValue(json, typeReference);  // Thread safe ?
		} catch (Exception ex) {
			throw new MappingException(ex);
		}
	}
}
