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

import org.rd.fullstack.springbooteda.util.hazelcast.HazelcastConstants;
import org.rd.fullstack.springbooteda.util.hazelcast.HazelcastSandbox;
import org.rd.fullstack.springbooteda.util.hazelcast.Mode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HazelcastConfig {

    @Value("${org.rd.fullstack.springbooteda.hazelcast.sandbox.enabled}")
    private boolean fEnabled;

    @Value("${org.rd.fullstack.springbooteda.hazelcast.sandbox.instance-name}")
    private String instanceName;

    @Value("${org.rd.fullstack.springbooteda.hazelcast.sandbox.cluster-name}")
    private String clusterName;

    @Value("${org.rd.fullstack.springbooteda.hazelcast.sandbox.port}")
    private int port;

    @Value("${org.rd.fullstack.springbooteda.hazelcast.sandbox.port-count}")
    private int portCount;

    @Value("${org.rd.fullstack.springbooteda.hazelcast.sandbox.port-auto-increment}")
    private boolean portAutoIncrement;

    @Value("${org.rd.fullstack.springbooteda.hazelcast.sandbox.join.multicast.enabled}")
    private boolean multicastEnabled;

    @Value("${org.rd.fullstack.springbooteda.hazelcast.sandbox.join.tcp-ip.enabled}")
    private boolean tcpIpEnabled;

    @Value("${org.rd.fullstack.springbooteda.hazelcast.sandbox.join.tcp-ip.members}")
    private String tcpIpMembers;

    @Value("${org.rd.fullstack.springbooteda.hazelcast.sandbox.cp-subsystem.cp-member-count}")
    private int cpMemberCount;

    @Value("${org.rd.fullstack.springbooteda.hazelcast.sandbox.cp-subsystem.session-heartbeat-interval-seconds}")
    private int sessionHeartbeatIntervalSeconds;

    @Value("${org.rd.fullstack.springbooteda.hazelcast.sandbox.cp-subsystem.session-time-to-live-seconds}")
    private int sessionTimeToLiveSeconds;

    @Bean
    HazelcastSandbox getHazelcastSandbox() throws Exception {
        return HazelcastSandbox.builder()
            .mode(Mode.SERVER)
            .instanceName(instanceName)
            .clusterName(clusterName)
            .port(port)
            .portCount(portCount)
            .portAutoIncrement(portAutoIncrement)
            .multicastEnabled(multicastEnabled)
            .tcpIpMembers(tcpIpMembers)
            .tcpIpEnabled(tcpIpEnabled)
            .addMap(HazelcastConstants.CST_MAPNAME_CTX)
            .addMap(HazelcastConstants.CST_MAPNAME_STATS)
            .addMap(HazelcastConstants.CST_MAPNAME_CLIENT_LOCKS)
            .cpMemberCount(cpMemberCount)
            .sessionHeartbeatIntervalSeconds(sessionHeartbeatIntervalSeconds)
            .sessionTimeToLiveSeconds(sessionTimeToLiveSeconds)
            .autoStart(fEnabled)
            .build();
    }     
}