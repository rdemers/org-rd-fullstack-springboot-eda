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

package org.rd.fullstack.springbooteda;

import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;

public abstract class AbstractMVC {

   protected static MockMvc mvcInstance = null;
   protected static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Autowired
    WebApplicationContext webApplicationContext;

    public AbstractMVC() {
       super();
    }

    protected synchronized MockMvc getMvcInstance() {
        if (mvcInstance == null)
          mvcInstance = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        return mvcInstance;
    }

    protected String mapToJson(Object obj) throws Exception {
       ObjectMapper objectMapper = new ObjectMapper();
       return objectMapper.writeValueAsString(obj);
    }

    protected <T> T mapFromJson(String json, Class<T> clazz) throws Exception {
       ObjectMapper objectMapper = new ObjectMapper();
       return objectMapper.readValue(json, clazz);
    }
}