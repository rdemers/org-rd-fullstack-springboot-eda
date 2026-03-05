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
package org.rd.fullstack.springbooteda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.rd.fullstack.springbooteda.config.Application;
import org.rd.fullstack.springbooteda.dto.Person;
import org.rd.fullstack.springbooteda.util.JsonMapper;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.core.type.TypeReference;

/*
 * See POM.XML file
 * - Plugins section: maven-surefire-plugin
 * - Unit tests VS integrated tests.
 */
@SpringBootTest(classes = Application.class)
@WebAppConfiguration
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Controller tests for Person entities.")
public class T8100_PersonController_UT_Tests extends AbstractMVC {

    private final String CST_URI_PERSONS = "/api/persons";

    public T8100_PersonController_UT_Tests() {
        super();
        mvcInstance = null;
    }

    @Test
    @Order(1)
    public void postPerson() throws Exception {
        MockMvc mvcInstance  = getMvcInstance();
        assertNotNull(mvcInstance);

        Person person = new Person("firstName-01", "lastName-01", new BigDecimal("1000.00"));
        String inputJson = JsonMapper.writeToJson(person);
        assertNotNull(inputJson);

        MvcResult mvcResult = mvcInstance.perform(MockMvcRequestBuilders.post(CST_URI_PERSONS)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .content(inputJson))
            .andReturn();

        int status = mvcResult.getResponse().getStatus();
        assertEquals(201, status); // Created.

        String content = mvcResult.getResponse().getContentAsString();
        assertNotNull(content);

        person = JsonMapper.readFromJson(content, Person.class);
        assertNotNull(person,"postPerson(), person is NULL.");
        logger.info("The post response message : {}.", person);
    }

    @Test
    @Order(2)
    public void getPersons() throws Exception {
        MockMvc mvcInstance  = getMvcInstance();
        assertNotNull(mvcInstance);

        MvcResult mvcResult = mvcInstance.perform(MockMvcRequestBuilders.get(CST_URI_PERSONS)
                .accept(MediaType.APPLICATION_JSON_VALUE))
                .andReturn();

        int status = mvcResult.getResponse().getStatus();
        assertEquals(200, status);

        String content = mvcResult.getResponse().getContentAsString();
        assertNotNull(content);

        List<Person> persons = JsonMapper.readFromJson(content, new TypeReference<>() {});
        persons.forEach(person -> logger.info("The get response message : {}.", person.toString()));
    }
}