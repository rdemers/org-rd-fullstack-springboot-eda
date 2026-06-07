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
package org.rd.fullstack.springbooteda.util.hazelcast;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.cluster.Cluster;
import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.cp.CPSubsystemConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.CPSubsystem;
import com.hazelcast.map.IMap;
import com.hazelcast.map.LocalMapStats;
import com.hazelcast.partition.PartitionService;

public class HazelcastSandbox implements AutoCloseable {

    private static final Logger logger =
        LoggerFactory.getLogger(HazelcastSandbox.class);

    private Config config;
    private volatile HazelcastInstance hazelcastInstance;

    private HazelcastSandbox(Config config) {
        this.config = config;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Mode                   mode;
        private String                 instanceName;
        private String                 clusterName;
        private int                    port;
        private int                    portCount;
        private boolean                portAutoIncrement;
        private boolean                multicastEnabled;
        private String                 tcpIpMembers;
        private boolean                tcpIpEnabled;
        private Map<String, MapConfig> mapMapConfig;
        private int                    cpMemberCount;
        private int                    sessionHeartbeatIntervalSeconds;
        private int                    sessionTimeToLiveSeconds;
        private boolean                autoStart;

        public Builder() {
            this.mapMapConfig = new java.util.HashMap<>();
        }

        public Builder mode(Mode mode) {
            this.mode = mode;
            return this;
        }

        public Builder instanceName(String instanceName) {
            this.instanceName = instanceName;
            return this;
        }
        public Builder clusterName(String clusterName) {
            this.clusterName = clusterName;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder portCount(int portCount) {
            this.portCount = portCount;
            return this;
        }

        public Builder portAutoIncrement(boolean portAutoIncrement) {
            this.portAutoIncrement = portAutoIncrement;
            return this;
        }

        public Builder multicastEnabled(boolean multicastEnabled) {
            this.multicastEnabled = multicastEnabled;
            return this;
        }

        public Builder tcpIpMembers(String tcpIpMembers) {
            this.tcpIpMembers = tcpIpMembers;
            return this;
        }

        public Builder tcpIpEnabled(boolean tcpIpEnabled) {
            this.tcpIpEnabled = tcpIpEnabled;
            return this;
        }

        public Builder addMap(String mapName) {
            this.mapMapConfig.putIfAbsent(mapName, new MapConfig().setName(mapName));
            return this;
        }

        public Builder cpMemberCount(int cpMemberCount) {
            this.cpMemberCount = cpMemberCount;
            return this;
        }

        public Builder sessionHeartbeatIntervalSeconds(int sessionHeartbeatIntervalSeconds) {
            this.sessionHeartbeatIntervalSeconds = sessionHeartbeatIntervalSeconds;
            return this;
        }

        public Builder sessionTimeToLiveSeconds(int sessionTimeToLiveSeconds) {
            this.sessionTimeToLiveSeconds = sessionTimeToLiveSeconds;
            return this;
        }

        public Builder autoStart(boolean autoStart) {
            this.autoStart = autoStart;
            return this;
        }

        public HazelcastSandbox build() throws Exception {
            Config config = new Config();

            // Bind Hazelcast's (de)serialization to the classloader that loaded the
            // application classes. Without this, Hazelcast deserializes IMap values with
            // the loader that loaded Hazelcast itself (the base 'app' loader). Under Spring
            // Boot DevTools the application classes live in a throwaway RestartClassLoader,
            // so the deserialized type and the type expected by the app code share the same
            // name but different loaders -> ClassCastException. Capturing the current
            // context classloader here (the RestartClassLoader at bean-creation time, the
            // normal app loader in production) keeps both sides consistent.
            config.setClassLoader(Thread.currentThread().getContextClassLoader());

            config.setInstanceName(instanceName);
            config.setClusterName(clusterName);

            NetworkConfig network = config.getNetworkConfig();
            network.setPort(port);
            network.setPortCount(portCount);
            network.setPortAutoIncrement(portAutoIncrement);

            JoinConfig join = network.getJoin();
            join.getMulticastConfig().setEnabled(multicastEnabled);
            join.getTcpIpConfig().addMember(tcpIpMembers).setEnabled(tcpIpEnabled);

            mapMapConfig.forEach((mapName, mapConfig) -> {
                if (mapConfig.getName() == null || mapConfig.getName().isBlank()) {
                    mapConfig.setName(mapName);
                }
                //mapConfig.setBackupCount(1);
                //mapConfig.setTimeToLiveSeconds(3600);
                //mapConfig.addIndexConfig(new IndexConfig(IndexType.HASH, "A valid field ..."));
                
                config.addMapConfig(mapConfig);
            });

            // CP Subsystem — number of CP members in the Raft cluster.
            // 0  : CP Subsystem disabled (UNSAFE mode, FencedLock unavailable).
            // 1  : UNSAFE mode enabled → acceptable for local testing only.
            // 3+ : strict CP (Raft) mode → mandatory in production.
            CPSubsystemConfig cpConfig = config.getCPSubsystemConfig();
            cpConfig.setCPMemberCount(cpMemberCount);

            if (cpMemberCount > 0) {
                cpConfig.setSessionHeartbeatIntervalSeconds(sessionHeartbeatIntervalSeconds);
                cpConfig.setSessionTimeToLiveSeconds(sessionTimeToLiveSeconds);
            }

            HazelcastSandbox hazelcastSandbox = new HazelcastSandbox(config);
            if (autoStart) {
                hazelcastSandbox.start(mode);
            }
            return hazelcastSandbox;
        }
    }
    
    public HazelcastDashboard getDashboardData() throws Exception {

        requireStarted();

        Cluster cluster     = hazelcastInstance.getCluster();
        String instanceName = hazelcastInstance.getName();
        String clusterName  = hazelcastInstance.getConfig().getClusterName();
        String clusterState = cluster.getClusterState().name();
        long   clusterTime  = cluster.getClusterTime();

        List<Member> members = cluster.getMembers()
            .stream()
            .map(m -> new Member(
                m.getAddress().toString(),
                m.getUuid().toString(),
                m.localMember(),
                m.isLiteMember()
            ))
            .toList();

        logger.debug("Hazelcast dashboard — cluster '{}' état={} membres={}.",
            clusterName, clusterState, members.size());

        List<Stats> mapStats = List.of(this.config.getMapConfigs().keySet().toArray(new String[0]))
            .stream()
            .map(mapName -> {
                @SuppressWarnings("null")
                IMap<?, ?> map = hazelcastInstance.getMap(mapName);
                LocalMapStats stats = map.getLocalMapStats();
                return new Stats(
                    mapName,
                    stats.getOwnedEntryCount(),
                    stats.getBackupEntryCount(),
                    stats.getHits(),
                    0, // stats.getMissCount() is not available in local map stats.
                     // It is only available in the cluster-wide map stats, which are not accessible from the local member.
                     // For simplicity, we will set it to 0 here, but it can be calculated as total get operations minus hits.
                     // Alternatively, we could retrieve the cluster-wide map stats and get the miss count from there.
                     // For now, we will leave it as 0 to avoid additional complexity.
                    stats.getGetOperationCount(),
                    stats.getPutOperationCount(),
                    stats.getRemoveOperationCount(),
                    stats.getLockedEntryCount(),
                    stats.getHeapCost(),
                    stats.getLastAccessTime(),
                    stats.getLastUpdateTime()
                );
            })
            .toList();

        PartitionService ps = hazelcastInstance.getPartitionService();

        int  totalPartitions = ps.getPartitions().size();
        @SuppressWarnings("null")
        long localPartitions = ps.getPartitions()
            .stream()
            .filter(p -> p.getOwner() != null && p.getOwner().localMember())
            .count();
        boolean clusterSafe       = ps.isClusterSafe();
        boolean localMemberSafe   = ps.isLocalMemberSafe();
        boolean migrationInProgress = !clusterSafe;

        PartitionInfo partitionInfo = new PartitionInfo(
            totalPartitions,
            localPartitions,
            clusterSafe,
            localMemberSafe,
            migrationInProgress
        );

        logger.debug("Hazelcast dashboard — partitions total={} local={} sain={}.",
            totalPartitions, localPartitions, clusterSafe);

        return new HazelcastDashboard(
            instanceName,
            clusterName,
            clusterState,
            clusterTime,
            members,
            mapStats,
            partitionInfo
        );
    }

    private void requireStarted() {
        if (hazelcastInstance == null)
            throw new IllegalStateException("HazelcastSandbox must be started first.");
    }
    
    public synchronized void start(Mode mode) throws Exception {
        if (hazelcastInstance != null)
            return;

        switch (mode) {
            case SERVER:
                hazelcastInstance = Hazelcast.getOrCreateHazelcastInstance(config);
                logger.info("Hazelcast instance '{}' has been started.", hazelcastInstance.getName());
                break;
            case CLIENT:
                ClientConfig clientConfig = new ClientConfig();
                clientConfig.setClusterName(config.getClusterName());
                hazelcastInstance = HazelcastClient.newHazelcastClient(clientConfig);
                logger.info("Hazelcast client instance '{}' has been started.", hazelcastInstance.getName());
                break;
            default:
                throw new IllegalArgumentException("Invalid Hazelcast mode: " + mode);
        }
    }

    public synchronized void stop() {
        if (hazelcastInstance == null)
            return;

        try {
            hazelcastInstance.shutdown();
        } catch (Exception ex) {
            logger.warn("Exception closing Hazelcast Instance: {}.", ex);
        } finally {
            hazelcastInstance  = null;
        }
    }

    @Override
    public void close() throws Exception {
        stop();
    }

    public <K, V> IMap<K, V> getMap(String mapname) {
        requireStarted();
        Objects.requireNonNull(mapname, "Map name must not be null.");

        return hazelcastInstance.getMap(mapname);
    }

    public CPSubsystem getCPSubsystem() {
        requireStarted();
        return hazelcastInstance.getCPSubsystem();
    }

    /**
     * Exposes the live Hazelcast instance (cluster, partition service, etc.).
     * Intended for diagnostics/observability and tests; callers must not shut it down directly.
     */
    public HazelcastInstance getHazelcastInstance() {
        requireStarted();
        return hazelcastInstance;
    }
}