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

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.rd.fullstack.springbooteda.config.Application;
import org.rd.fullstack.springbooteda.dto.PipelineContext;
import org.rd.fullstack.springbooteda.dto.StatsContext;
import org.rd.fullstack.springbooteda.srv.PipelineSrv;
import org.rd.fullstack.springbooteda.util.JsonMapper;
import org.rd.fullstack.springbooteda.util.PipelineState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/*
 * See POM.XML file
 * - Plugins section: maven-surefire-plugin
 * - Unit tests VS integrated tests.
 */
@WebAppConfiguration
@SpringBootTest(classes = Application.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Controller tests for processor.")
public class T8700_PipelineController_UT_Tests extends AbstractMVC {

    private final String CST_URI_PROCESSOR = "/api/pipeline";

    public T8700_PipelineController_UT_Tests() {
        super();
        mvcInstance = null;
    }

    @Autowired
    PipelineSrv pipelineService;

    @Test
    @Order(1)
    public void postStart() throws Exception {
        MockMvc mvcInstance  = getMvcInstance();
        assertNotNull(mvcInstance);

        // The body now carries only the run PARAMETERS (counters/state live in StatsContext).
        PipelineContext pipelineContext = new PipelineContext();
        pipelineContext.setKey(true);
        String inputJson = JsonMapper.writeToJson(pipelineContext);
        assertNotNull(inputJson);

        MvcResult mvcResult = mvcInstance.perform(MockMvcRequestBuilders.post(CST_URI_PROCESSOR + "/start")
            .header("Authorization", "Bearer " + CST_JWT_TOKEN)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .accept(MediaType.APPLICATION_JSON_VALUE)
            .content(inputJson))
            .andReturn();

        int status = mvcResult.getResponse().getStatus();
        assertEquals(201, status); // Created.

        String content = mvcResult.getResponse().getContentAsString();
        assertNotNull(content);

        pipelineContext = JsonMapper.readFromJson(content, PipelineContext.class);
        assertNotNull(pipelineContext, "postStart(), PipelineContext is NULL.");
        assertTrue(Boolean.TRUE.equals(pipelineContext.getKey()));
        logger.info("The post response message: {}.", pipelineContext.toString());
    }

    @Test
    @Order(2)
    public void getStats() throws Exception {
        MockMvc mvcInstance  = getMvcInstance();
        assertNotNull(mvcInstance);

        MvcResult mvcResult = mvcInstance.perform(MockMvcRequestBuilders.get(CST_URI_PROCESSOR + "/stats")
                .header("Authorization", "Bearer " + CST_JWT_TOKEN)
                .accept(MediaType.APPLICATION_JSON_VALUE))
                .andReturn();

        int status = mvcResult.getResponse().getStatus();
        assertEquals(200, status);

        String content = mvcResult.getResponse().getContentAsString();
        assertNotNull(content);

        StatsContext statsContext = JsonMapper.readFromJson(content, StatsContext.class);
        assertNotNull(statsContext, "getStats(), StatsContext is NULL.");
        // A run was initiated in postStart(): the state must no longer be READY.
        assertNotNull(statsContext.getPipelineState());
        assertTrue(statsContext.getPipelineState() != PipelineState.READY);
        logger.info("The get response message: {}.", statsContext.toString());
    }

    @Test
    @Order(3)
    public void postReset() throws Exception {
        MockMvc mvcInstance  = getMvcInstance();
        assertNotNull(mvcInstance);

        MvcResult mvcResult = mvcInstance.perform(MockMvcRequestBuilders.post(CST_URI_PROCESSOR + "/reset")
            .header("Authorization", "Bearer " + CST_JWT_TOKEN)
            .contentType(MediaType.APPLICATION_JSON_VALUE)
            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andReturn();

        int status = mvcResult.getResponse().getStatus();
        assertTrue("Expected 201 or 409",
            status == 201 || status == 409); // Created or Conflict.

        if (status == 409) {
            logger.warn("The pipeline is in an invalid state for a reset. OK.");
            return;
        }

        String content = mvcResult.getResponse().getContentAsString();
        assertNotNull(content);

        StatsContext statsContext = JsonMapper.readFromJson(content, StatsContext.class);
        assertNotNull(statsContext, "postReset(), StatsContext is NULL.");
        assertTrue(statsContext.getPipelineState() == PipelineState.READY);
        logger.info("The post response message: {}.", statsContext.toString());
    }
}
