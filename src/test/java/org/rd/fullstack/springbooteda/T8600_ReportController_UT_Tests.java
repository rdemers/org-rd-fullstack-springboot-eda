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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.rd.fullstack.springbooteda.config.Application;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/*
 * See POM.XML file
 * - Plugins section: maven-surefire-plugin
 * - Unit tests VS integrated tests.
 */
@SpringBootTest(classes = Application.class)
@WebAppConfiguration
@ContextConfiguration
@WithMockUser(username="root",roles={"SELECT","INSERT","UPDATE","DELETE"})
// @ActiveProfiles("test") If specific configuration file. Example: application-test.yml
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Controller tests for reporting.")
public class T8600_ReportController_UT_Tests extends AbstractMVC {

    private final String uriReport = "/report/invents-report";

    public T8600_ReportController_UT_Tests() {
        super();
        mvcInstance = null;
    }

    @Test
    public void getReportBooks() throws Exception {
        MockMvc mvcInstance  = getMvcInstance();
        assertNotNull(mvcInstance,"The getMvcInstance() method return NULL.");

        MvcResult mvcResult = mvcInstance.perform(MockMvcRequestBuilders.get(uriReport)
                //.with(SecurityMockMvcRequestPostProcessors.user("root").password("root").roles("SELECT"))
                // See class annotation : @WithMockUser.
                .accept(MediaType.APPLICATION_PDF_VALUE))
                .andReturn();

        int status = mvcResult.getResponse().getStatus();
        assertEquals(200, status);

        byte[] pdfByteArray = mvcResult.getResponse().getContentAsByteArray();
        assertNotNull(pdfByteArray,"The response is NULL.");
    }
}