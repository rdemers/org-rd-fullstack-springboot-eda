/*
 * Copyright 2023; Réal Demers.
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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.rd.fullstack.springbooteda.config.Application;
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
@DisplayName("Controller tests for health.")
public class T8000_HealthController_UT_Tests extends AbstractMVC {

    public T8000_HealthController_UT_Tests() {
        super();
        mvcInstance = null;
    }

    @Test
    @Order(1)
    public void getLivenessStateDown() throws Exception {
        MockMvc mvcInstance  = getMvcInstance();
        assertNotNull(mvcInstance);

        String CST_URI_HEALT_LIVENESS_STATE_DOWN = "/api/liveness_state_down";
        MvcResult mvcResult = mvcInstance.perform(MockMvcRequestBuilders.get(CST_URI_HEALT_LIVENESS_STATE_DOWN)
                .accept(MediaType.ALL))
                .andReturn();

        int status = mvcResult.getResponse().getStatus();
        assertEquals(200, status);

        String content = mvcResult.getResponse().getContentAsString();
        assertNotNull(content);
        logger.info("LivenessStateDown - Content: {}.", content);
    }

    @Test
    @Order(2)
    public void getLivenessStateUp() throws Exception {
        MockMvc mvcInstance  = getMvcInstance();
        assertNotNull(mvcInstance);

        String CST_URI_HEALT_LIVENESS_STATE_UP = "/api/liveness_state_up";
        MvcResult mvcResult = mvcInstance.perform(MockMvcRequestBuilders.get(CST_URI_HEALT_LIVENESS_STATE_UP)
                .accept(MediaType.ALL))
                .andReturn();

        int status = mvcResult.getResponse().getStatus();
        assertEquals(200, status);

        String content = mvcResult.getResponse().getContentAsString();
        assertNotNull(content);
        logger.info("Customize Toolbar - Content: {}.", content);
    }

    @Test
    @Order(3)
    public void getReadinessStateDown() throws Exception {
        MockMvc mvcInstance  = getMvcInstance();
        assertNotNull(mvcInstance);

        String CST_URI_HEALT_READINESS_STATE_DOWN = "/api/readiness_state_down";
        MvcResult mvcResult = mvcInstance.perform(MockMvcRequestBuilders.get(CST_URI_HEALT_READINESS_STATE_DOWN)
                .accept(MediaType.ALL))
                .andReturn();

        int status = mvcResult.getResponse().getStatus();
        assertEquals(200, status);

        String content = mvcResult.getResponse().getContentAsString();
        assertNotNull(content);
        logger.info("ReadinessStateDown - Content: {}.", content);
    }

    @Test
    @Order(4)
    public void getReadinessStateUp() throws Exception {
        MockMvc mvcInstance  = getMvcInstance();
        assertNotNull(mvcInstance);

        String CST_URI_HEALT_READINESS_STATE_UP = "/api/readiness_state_up";
        MvcResult mvcResult = mvcInstance.perform(MockMvcRequestBuilders.get(CST_URI_HEALT_READINESS_STATE_UP)
                .accept(MediaType.ALL))
                .andReturn();

        int status = mvcResult.getResponse().getStatus();
        assertEquals(200, status);

        String content = mvcResult.getResponse().getContentAsString();
        assertNotNull(content);
        logger.info("ReadinessStateUp - Content: {}.", content);
    }

    @Test
    @Order(5)
    public void getKafkaDashboardData() throws Exception {
        MockMvc mvcInstance  = getMvcInstance();
        assertNotNull(mvcInstance);

        String CST_URI_HEALTH_KAFKA_DASHBOARD = "/api/kafkaDashboardData";
        MvcResult mvcResult = mvcInstance.perform(MockMvcRequestBuilders.get(CST_URI_HEALTH_KAFKA_DASHBOARD)
                .accept(MediaType.ALL))
                .andReturn();

        int status = mvcResult.getResponse().getStatus();
        assertEquals(200, status);

        String content = mvcResult.getResponse().getContentAsString();
        assertNotNull(content);
        logger.info("kafkaDashboardData - Content: {}.", content);
    }

    @Test
    @Order(6)
    public void getFlinkDashboardData() throws Exception {
        MockMvc mvcInstance  = getMvcInstance();
        assertNotNull(mvcInstance);

        String CST_URI_HEALTH_FLINK_DASHBOARD = "/api/flinkDashboardData";
        MvcResult mvcResult = mvcInstance.perform(MockMvcRequestBuilders.get(CST_URI_HEALTH_FLINK_DASHBOARD)
                .accept(MediaType.ALL))
                .andReturn();

        int status = mvcResult.getResponse().getStatus();
        assertEquals(200, status);

        String content = mvcResult.getResponse().getContentAsString();
        assertNotNull(content);
        logger.info("flinkDashboardData - Content: {}.", content);
    }
}