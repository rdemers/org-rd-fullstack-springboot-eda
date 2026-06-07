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
package org.rd.fullstack.springbooteda.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
//@EnableWebMvc -- This disables all autoconfiguration, including static resource management.
public class WebConfig {
    @Bean
    WebMvcConfigurer forwardToIndex() {
        return new WebMvcConfigurer() {
            @Override
            public void addViewControllers(ViewControllerRegistry registry) {

                // Swagger UI interface.
                registry
                        .addViewController("/swagger-ui") 
                        .setViewName("redirect:/swagger-ui/index.html");
                
                // Nuxt application. 
                // Att : MINIMUM CONFIGURATION.
                registry
                        .addViewController("/favicon.ico")
                        .setViewName("/app/favicon.ico");
                registry
                        .addViewController("/app")
                        .setViewName("/app/index.html");
                registry
                        .addViewController("/app/")                        
                        .setViewName("/app/index.html");
            }
        };
    }
}