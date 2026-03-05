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

import org.rd.fullstack.springbooteda.util.flink.FlinkSandbox;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlinkConfig {

    @Value("${org.rd.fullstack.springbooteda.flink.sandbox.enabled}")
    private boolean fEnabled;

    @Value("${org.rd.fullstack.springbooteda.flink.sandbox.rest-port}")
    private int restPort;

    @Value("${org.rd.fullstack.springbooteda.flink.sandbox.job-manager-port}")
    private int jobManagerPort;

    @Value("${org.rd.fullstack.springbooteda.flink.sandbox.task-manager-rpc-port}")
    private String taskManagerRpcPort;

    @Value("${org.rd.fullstack.springbooteda.flink.sandbox.num-task-managers}")
    private int numTaskManagers;

    @Value("${org.rd.fullstack.springbooteda.flink.sandbox.num-slots-per-task-manager}")
    private short numSlotsPerTaskManager;

    @Value("${org.rd.fullstack.springbooteda.flink.sandbox.active-metrics}")
    private boolean fActiveMetrics;

    @Bean
    FlinkSandbox getFlinkSandbox() throws Exception {
        return FlinkSandbox.builder()
            .restPort(restPort)
            .jobManagerPort(jobManagerPort)
            .taskManagerRpcPort(taskManagerRpcPort) 
            .numTaskManagers(numTaskManagers)
            .numSlotsPerTaskManager(numSlotsPerTaskManager)
            .activeMetrics(fActiveMetrics)
            .autoStart(fEnabled)
            .build();
    }
}