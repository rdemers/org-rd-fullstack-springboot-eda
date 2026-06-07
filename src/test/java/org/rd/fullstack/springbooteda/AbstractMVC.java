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

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

public abstract class AbstractMVC {
   protected static MockMvc mvcInstance  = null;
   protected static final Logger logger  = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    protected final String CST_JWT_TOKEN = 
        "eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiIzNzJjMDhjMS02OGY5LTQ3YmQtODdhOS1iNjY0YTIxZTZhMWMiLCJzdWIiOiJyb290IiwicmQucm9sZXMiOiJST0xFX1NFTEVDVCxST0xFX0lOU0VSVCxST0xFX1VQREFURSxST0xFX0RFTEVURSIsImlhdCI6MTcxODM4NjQ1NH0.AIS4YFJCUIIv0IzTxdDp3MqQXDyuhQBFSJnJmF6b06c";

    @Autowired
    WebApplicationContext webApplicationContext;

    protected synchronized MockMvc getMvcInstance() {

      if (mvcInstance == null)
          mvcInstance = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

      return mvcInstance;
    }
}